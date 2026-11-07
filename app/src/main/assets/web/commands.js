/* Yo'ldosh command understanding (Russian + Uzbek, offline).
 * parse(text) -> { intent, arg }   — what the driver wants
 * Intents are executed in app.js.
 */
(function () {
  function norm(s) {
    return (s || '')
      .toLowerCase()
      .replace(/ё/g, 'е')
      .replace(/[ʻʼ’‘`´']/g, '')          // o'zbek apostrophes vary: drop them all
      .replace(/[.,!?;:«»"()]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  // Strip the wake words so "салом йолдош включи музыку" == "включи музыку"
  function stripWake(t) {
    return t
      .replace(/^(эй |hey |hey |ey )?/, '')
      .replace(/^(салом|салам|привет|salom|assalomu alaykum|ассалому алайкум)\s*/, '')
      .replace(/^(йолдош|ёлдош|йулдош|юлдош|yoldosh|yuldosh|yo ldosh)\s*/, '')
      .trim();
  }

  var rules = [
    // ---- music
    { intent: 'pause', re: /^(пауза|стоп|останови|выключи (музыку|песню)|хватит|tuxtat|toxtat|pauza|stop|musiqani ochir|ochir musiqa)/ },
    { intent: 'next', re: /(следующ|дальше|переключи|другую песню|другой трек|пропусти|keyingi|boshqa qoshiq|almashtir)/ },
    { intent: 'prev', re: /(предыдущ|верни прошл|прошлую песню|oldingi)/ },
    { intent: 'play', re: /(включи музык|вруби музык|поставь музык|включи песн|играй|продолжи|музыку|музыка|musiqa|qoshiq|qushiq|ashula|davom et)/ },

    // ---- volume
    { intent: 'volUp', re: /(громче|погромче|прибавь|увеличь громкость|baland|balandla|ovozni kotar|ovozni oshir)/ },
    { intent: 'volDown', re: /(тише|потише|убавь|уменьши громкость|sekinroq|pasaytir|ovozni pasay|ovozni kamaytir|past qil)/ },

    // ---- navigation
    { intent: 'navHome', re: /(домой|до дома|еду дом|uyga|uyimga)/ },
    { intent: 'navWork', re: /(на работу|до работы|ishga|ishxonaga)/ },
    { intent: 'navCharge', re: /(заряд|зарядк|электрозаправ|zaryad|quvvat)/ },
    { intent: 'navFuel', re: /(заправк|азс|бензин|пропан|метан|zapravka|benzin|yoqilgi)/ },
    { intent: 'navTo', re: /(?:поехали|едем|маршрут|проложи маршрут|отвези|вези|навигация|построй маршрут|найди)\s+(?:в |во |на |до |к |ко )?(.+)/, arg: 1 },
    { intent: 'navTo', re: /^(.+?)(?:ga|ka|qa) (?:boramiz|bor|olib bor|yur|yuramiz|haydaymiz)$/, arg: 1 },
    { intent: 'navTo', re: /^(.+?) (?:qayerda|qani)$/, arg: 1 },
    { intent: 'openNavi', re: /(открой навигатор|навигатор|карту|карты|navigator|xarita)/ },

    // ---- car (needs vehicle access – answered honestly). Before "apps" so "открой багажник" is not an app.
    { intent: 'car', re: /(окн|багажник|кондиционер|климат|печк|подогрев|обогрев|вентиляц|сиден|руль|руля|зеркал|фар|двер|замк|люк|температур|градус|oyna|bagaj|bagajnik|konditsioner|konditsaner|isit|sovut|orindiq|rul|eshik|chiroq|harorat|gradus|qulf)/ },

    // ---- apps
    { intent: 'openMusic', re: /(открой (яндекс )?музык|yandeks musiqa)/ },
    { intent: 'openApp', re: /^(?:открой|запусти|включи приложение)\s+(.+)/, arg: 1 },
    { intent: 'openApp', re: /^(.+?)(?:ni)? (?:och|ochib ber|ishga tushir)$/, arg: 1 },

    // ---- small talk
    { intent: 'time', re: /(который час|сколько времени|сколько время|время|soat necha|soat nechchi|vaqt)/ },
    { intent: 'date', re: /(какое (сегодня )?число|какой (сегодня )?день|bugun nechanchi|qaysi kun|sana)/ },
    { intent: 'thanks', re: /(спасибо|благодарю|rahmat|raxmat|tashakkur)/ },
    { intent: 'who', re: /(кто ты|как тебя зовут|ты кто|sen kimsan|isming nima|kimsan)/ },
    { intent: 'help', re: /(что ты умеешь|помощь|помоги|nima qila olasan|yordam)/ },
    { intent: 'hello', re: /^(привет|здравствуй|салом|салам|salom|assalomu alaykum|hello)$/ },
    { intent: 'close', re: /^(отмена|ничего|закрой|все|всё|bekor|hech narsa|yop)$/ },
  ];

  // Numbers 16..33 for climate, spoken as words (ru/uz) or digits.
  function numFromText(t) {
    var digit = t.match(/\b(\d{1,2})\b/); if (digit) return +digit[1];
    var toks = t.split(' ');
    var teens = { 'шестнадцать': 16, 'семнадцать': 17, 'восемнадцать': 18, 'девятнадцать': 19 };
    var tens = { 'двадцать': 20, 'тридцать': 30, 'yigirma': 20, 'ottiz': 30, 'on': 10 };
    var units = {
      'один': 1, 'два': 2, 'три': 3, 'четыре': 4, 'пять': 5, 'шесть': 6, 'семь': 7, 'восемь': 8, 'девять': 9,
      'bir': 1, 'ikki': 2, 'uch': 3, 'tort': 4, 'besh': 5, 'olti': 6, 'yetti': 7, 'sakkiz': 8, 'toqqiz': 9
    };
    for (var i = 0; i < toks.length; i++) if (teens[toks[i]] != null) return teens[toks[i]];
    var base = null, unit = null;
    for (var j = 0; j < toks.length; j++) {
      if (base == null && tens[toks[j]] != null) base = tens[toks[j]];
      if (unit == null && units[toks[j]] != null) unit = units[toks[j]];
    }
    if (base != null) return base + (unit || 0);
    return null;
  }

  // Structured comfort-control intents (climate / windows). Returns {intent, value} or null.
  function classifyCar(s) {
    var hasAc = /кондиционер|климат|konditsioner|konditsaner|harorat|харорат|температур|gradus|градус|печк|isit|sovut/.test(s);
    var hasWin = /окн|oyna/.test(s);
    var num = numFromText(s);
    var off = /выключ|ochir|убери|otklyuch|o.chir/.test(s);

    if (hasWin) {
      var closeW = /закрой|kotar|yop|подним|zakroy/.test(s);
      var openW = /открой|tushir|опусти|pastga|och|spusti/.test(s);
      return { intent: 'carWindow', value: (closeW && !openW) ? 0 : 1 };
    }
    if (num != null && num >= 16 && num <= 33 && (hasAc || /поставь|сделай|qoy|qo.y|ustan/.test(s))) {
      return { intent: 'carTemp', value: num };
    }
    if (hasAc) return { intent: 'carAc', value: off ? 0 : 1 };
    return null;
  }

  function parse(raw) {
    var t = norm(raw);
    var hadWakeOnly = /^(салом|салам|привет|salom)?\s*(йолдош|ёлдош|йулдош|юлдош|yoldosh|yuldosh)$/.test(t);
    if (hadWakeOnly) return { intent: 'hello', text: t };
    var s = stripWake(t);
    if (!s) return { intent: 'hello', text: t };
    var carHit = classifyCar(s);
    if (carHit) { carHit.text = s; return carHit; }
    for (var i = 0; i < rules.length; i++) {
      var m = s.match(rules[i].re);
      if (m) {
        var arg = rules[i].arg ? (m[rules[i].arg] || '').trim() : '';
        if (rules[i].arg && !arg) continue;
        return { intent: rules[i].intent, arg: arg, text: s };
      }
    }
    return { intent: 'unknown', text: s };
  }

  // Spoken app names → how they are usually labelled on the head unit
  var ALIASES = {
    'ютуб': 'youtube', 'ютюб': 'youtube', 'youtub': 'youtube', 'телеграм': 'telegram', 'телеграмм': 'telegram',
    'вотсап': 'whatsapp', 'ватсап': 'whatsapp', 'инстаграм': 'instagram', 'тикток': 'tiktok',
    'навигатор': 'навигатор', 'яндекс карты': 'карты', 'карты': 'карты', 'музыку': 'музыка', 'музыка': 'музыка',
    'контракам': 'contracam', 'контра кам': 'contracam', 'антирадар': 'contracam', 'радар': 'contracam',
    'браузер': 'browser', 'настройки': 'настройки', 'sozlamalar': 'настройки', 'spotify': 'spotify', 'спотифай': 'spotify'
  };
  function alias(name) {
    var n = norm(name);
    for (var k in ALIASES) { if (n.indexOf(k) >= 0) return ALIASES[k]; }
    return '';
  }

  window.YCommands = { parse: parse, norm: norm, alias: alias };
})();
