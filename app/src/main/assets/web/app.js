/* Yo'ldosh interface logic. window.Android is the Kotlin bridge; in a desktop browser a stub is used. */
(function () {
  'use strict';

  // ---------------------------------------------------------------- bridge
  var A = window.Android || {
    ready: function () {}, settings: function () { return JSON.stringify(stub.settings); },
    setSetting: function (k, v) { stub.settings[k] = v; }, requestPerm: function (k) { stub.perms[k] = true; setTimeout(function () { window.onNative('perms', stub.perms); }, 200); },
    listen: function () { setTimeout(function () { window.onNative('voice', { state: 'listening' }); }, 200); },
    stopListen: function () {}, speak: function () { return false; }, stopSpeaking: function () {},
    media: function () {}, mediaState: function () { return '{"access":false,"active":false}'; },
    volume: function (d) { return 50 + d * 10; }, navigate: function () { return ''; }, openApp: function () { return ''; },
    openPackage: function () { return false; }, installed: function () { return '[]'; },
    toast: function (t) { console.log(t); }, appVersion: function () { return 'preview'; }
  };
  var stub = { settings: { lang: 'uz', speak: true, bubble: true, autostart: false, onboarded: false, lastKey: -1, wheelKey: -1, tts: false, version: 'preview' },
               perms: { mic: false, location: false, overlay: false, notif: false, a11y: false, battery: false, autostart: false } };

  var $ = function (id) { return document.getElementById(id); };
  var errors = [];
  window.addEventListener('error', function (e) {
    errors.push(((e.filename || '').split('/').pop() + ':' + (e.lineno || '') + ' ' + (e.message || '')).slice(0, 120));
    if (errors.length > 3) errors.shift();
  });
  var settings = {};
  try { settings = JSON.parse(A.settings()); } catch (e) { settings = stub.settings; }
  var perms = {};
  var media = { access: false, active: false };

  // ---------------------------------------------------------------- scale the 1920x1080 layout to any screen
  // Design is 1920 wide; the stage stretches vertically (or horizontally) to fill any head unit screen.
  function fit() {
    var w = window.innerWidth, h = window.innerHeight;
    var stage = $('stage');
    var k, sw, sh;
    if (w / h >= 1920 / 1080) { k = h / 1080; sh = 1080; sw = w / k; }
    else { k = w / 1920; sw = 1920; sh = h / k; }
    stage.style.width = sw + 'px';
    stage.style.height = sh + 'px';
    stage.style.transform = 'scale(' + k + ')';
    stage.style.left = '0px';
    stage.style.top = '0px';
  }
  window.addEventListener('resize', fit);
  fit();

  // ---------------------------------------------------------------- texts
  var T = {
    ru: {
      listening: 'Слушаю…', loading: 'Загружаю голосовую модель… (только в первый раз)', thinking: 'Думаю…',
      you: 'Вы сказали', timeout: 'Ничего не услышал. Нажмите на микрофон ещё раз.', nomic: 'Нужен доступ к микрофону',
      error: 'Не получилось включить микрофон', tryThis: ['Включи музыку', 'Поехали домой', 'Найди зарядку', 'Громче', 'Который час'],
      hello: 'Салом! Чем помочь?', unknown: 'Пока не понял. Скажите, например: «Включи музыку» или «Поехали домой».',
      play: 'Включаю музыку', pause: 'Пауза', next: 'Следующий трек', prev: 'Предыдущий трек',
      volUp: 'Громче', volDown: 'Тише', noMusic: 'Открываю Яндекс Музыку',
      navHome: 'Строю маршрут домой', navWork: 'Строю маршрут на работу', navCharge: 'Ищу зарядки поблизости', navFuel: 'Ищу заправки рядом',
      navTo: 'Ищу: ', noNavi: 'Не нашёл Яндекс Навигатор или Карты на магнитоле', openNavi: 'Открываю навигатор',
      openApp: 'Открываю ', noApp: 'Не нашёл такое приложение: ',
      car: 'Это я подключу после диагностики машины — откройте «Диагностика машины» в настройках.',
      carFail: 'Пока не получилось управлять машиной на этой прошивке. Откройте «Диагностика машины» в настройках и пришлите мне результат.',
      acOn: 'Включаю кондиционер', acOff: 'Выключаю кондиционер',
      winOpen: 'Открываю окна', winClose: 'Закрываю окна', tempSet: 'Готово, климат на ',
      time: 'Сейчас ', thanks: 'Всегда пожалуйста!', who: 'Я Yo‘ldosh, ваш помощник в дороге.',
      help: 'Я включаю музыку, строю маршруты, открываю приложения и меняю громкость. Скоро научусь управлять машиной.',
      close: 'Хорошо'
    },
    uz: {
      listening: 'Tinglayapman…', loading: 'Ovoz modeli yuklanmoqda… (faqat birinchi marta)', thinking: 'O‘ylayapman…',
      you: 'Siz aytdingiz', timeout: 'Hech narsa eshitmadim. Mikrofonni yana bosing.', nomic: 'Mikrofonga ruxsat kerak',
      error: 'Mikrofonni yoqib bo‘lmadi', tryThis: ['Musiqa qo‘y', 'Uyga boramiz', 'Zaryadka qayerda', 'Balandroq', 'Soat necha'],
      hello: 'Salom! Qanday yordam beray?', unknown: 'Tushunmadim. Masalan: «Musiqa qo‘y» yoki «Uyga boramiz» deng.',
      play: 'Musiqani yoqyapman', pause: 'Pauza', next: 'Keyingi qo‘shiq', prev: 'Oldingi qo‘shiq',
      volUp: 'Balandroq', volDown: 'Pastroq', noMusic: 'Yandex Musiqani ochyapman',
      navHome: 'Uyga yo‘l tuzyapman', navWork: 'Ishga yo‘l tuzyapman', navCharge: 'Yaqin zaryadkalarni qidiryapman', navFuel: 'Yaqin zapravkalarni qidiryapman',
      navTo: 'Qidiryapman: ', noNavi: 'Yandex Navigator yoki Xaritalar topilmadi', openNavi: 'Navigatorni ochyapman',
      openApp: 'Ochyapman: ', noApp: 'Bunday ilova topilmadi: ',
      car: 'Buni mashina diagnostikasidan keyin ulayman — sozlamalardan «Diagnostika»ni oching.',
      carFail: 'Bu proshivkada hali boshqarib bo‘lmadi. Sozlamalardan «Diagnostika»ni ochib, natijani menga yuboring.',
      acOn: 'Konditsionerni yoqyapman', acOff: 'Konditsionerni o‘chiryapman',
      winOpen: 'Oynani tushiryapman', winClose: 'Oynani ko‘taryapman', tempSet: 'Bajarildi, harorat ',
      time: 'Hozir soat ', thanks: 'Arzimaydi!', who: 'Men Yo‘ldosh, yo‘ldagi yordamchingizman.',
      help: 'Musiqa qo‘yaman, yo‘l tuzaman, ilovalarni ochaman va ovozni o‘zgartiraman.',
      close: 'Xo‘p'
    }
  };
  function L() { return settings.lang === 'ru' ? 'ru' : 'uz'; }
  function t(k) { return T[L()][k]; }

  // ---------------------------------------------------------------- toast
  var toastTimer;
  function toast(text) {
    var el = $('toast');
    el.textContent = text; el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.hidden = true; }, 2600);
  }

  // ---------------------------------------------------------------- clock
  var MONTHS = { ru: ['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'],
                 uz: ['yanvar','fevral','mart','aprel','may','iyun','iyul','avgust','sentabr','oktabr','noyabr','dekabr'] };
  var DAYS = { ru: ['Воскресенье','Понедельник','Вторник','Среда','Четверг','Пятница','Суббота'],
               uz: ['Yakshanba','Dushanba','Seshanba','Chorshanba','Payshanba','Juma','Shanba'] };
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function hhmm(d) { return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  function dateText(d) {
    var l = L();
    return DAYS[l][d.getDay()] + ', ' + d.getDate() + ' ' + MONTHS[l][d.getMonth()];
  }
  function tick() {
    var d = new Date();
    $('time').textContent = hhmm(d);
    $('date').textContent = dateText(d);
  }
  setInterval(tick, 5000); tick();

  // ---------------------------------------------------------------- car controls (visual for now)
  var car = { locked: true, windows: false, trunk: false, mirrors: true, lights: false };
  var ICONS = {
    locked: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    windows: '<path d="M4 18V9l5-4h11v13z"/><path d="M12 9v6M9.5 12.5L12 15l2.5-2.5"/>',
    trunk: '<path d="M3 16l3-7h12l3 7v3H3z"/><path d="M6 9l3-4h6l3 4"/>',
    mirrors: '<path d="M4 8h9a3 3 0 0 1 3 3v2a3 3 0 0 1-3 3H4z"/><path d="M16 12h4"/>',
    lights: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"/>'
  };
  var ORBITS = { trunk: '200deg 75deg 95%', windows: '90deg 80deg 90%', mirrors: '50deg 80deg 85%', lights: '0deg 78deg 95%', locked: '35deg 78deg 105%' };
  function renderCar() {
    var items = [
      ['locked', car.locked ? 'Закрыто' : 'Открыто', false],
      ['windows', car.windows ? 'Окна открыты' : 'Окна', true],
      ['trunk', car.trunk ? 'Открыт' : 'Багажник', true],
      ['mirrors', 'Зеркала', false],
      ['lights', 'Свет', true]
    ];
    $('carControls').innerHTML = items.map(function (it) {
      var on = car[it[0]];
      return '<button class="ctl' + (it[2] ? ' warm' : '') + (on ? ' on' : '') + '" data-car="' + it[0] + '" aria-pressed="' + on + '">' +
        '<svg viewBox="0 0 24 24" aria-hidden="true">' + ICONS[it[0]] + '</svg><span>' + it[1] + '</span></button>';
    }).join('');
    $('carSub').textContent = car.locked ? 'Двери закрыты' : 'Двери открыты';
    var tag = $('trunkTag');
    tag.textContent = car.trunk ? 'Багажник открыт' : 'Багажник закрыт';
    tag.classList.toggle('on', car.trunk);
  }
  $('carControls').addEventListener('click', function (e) {
    var b = e.target.closest('[data-car]'); if (!b) return;
    var k = b.getAttribute('data-car');
    car[k] = !car[k];
    renderCar();
    var mv = $('car3d');
    if (has3d && mv && ORBITS[k]) { mv.setAttribute('camera-orbit', ORBITS[k]); }
    if (!has3d) showCarSide(k === 'trunk' ? 'rear' : 'front');
    if (k === 'windows') {
      var r = tryCar(car.windows ? 'window_open' : 'window_close', 0);
      toast(r.ok ? (car.windows ? 'Открываю окна' : 'Закрываю окна') : 'Окна подключу после диагностики машины (в настройках)');
    } else {
      toast('Эту функцию подключу после диагностики машины — в настройках');
    }
  });
  renderCar();

  // Still image by default; switch to the live 3D model only if this WebView can render it.
  var has3d = false, carSide = 'front';
  function showCarSide(side) {
    if (has3d || side === carSide) return;
    carSide = side;
    var img = $('carImg');
    img.classList.add('fade');
    setTimeout(function () { img.src = 'car_' + side + '.webp'; img.classList.remove('fade'); }, 300);
  }
  $('carImg').addEventListener('click', function () { showCarSide(carSide === 'front' ? 'rear' : 'front'); });
  (function try3d() {
    var mv = $('car3d');
    if (!mv || !window.customElements || !window.customElements.get || !window.customElements.get('model-viewer')) return;
    mv.addEventListener('load', function () {
      has3d = true;
      mv.classList.remove('hidden3d');
      $('carImg').style.display = 'none';
    });
  })();

  // ---------------------------------------------------------------- media
  function fmt(ms) { ms = Math.max(0, ms | 0); var s = Math.floor(ms / 1000); return Math.floor(s / 60) + ':' + pad(s % 60); }
  var posBase = 0, posAt = 0;
  function renderMedia() {
    $('mediaNotice').hidden = !!media.access;
    var playing = !!media.playing;
    $('playIcon').setAttribute('d', playing ? 'M7 5h4v14H7zM13 5h4v14h-4z' : 'M7 4v16l13-8z');
    $('playBtn').setAttribute('aria-label', playing ? 'Пауза' : 'Играть');
    if (!media.active) {
      var ru = settings.lang === 'ru';
      $('trackTitle').textContent = playing ? (ru ? 'Музыка играет' : 'Musiqa yangrayapti') : (ru ? 'Музыка на паузе' : 'Musiqa to‘xtatilgan');
      $('trackArtist').textContent = ru ? 'Яндекс Музыка' : 'Yandex Musiqa';
      $('cover').style.backgroundImage = ''; $('cover').classList.remove('has-art');
      $('progressBar').style.width = '0'; $('tPos').textContent = '0:00'; $('tDur').textContent = '0:00';
      return;
    }
    $('trackTitle').textContent = media.title || '—';
    $('trackArtist').textContent = media.artist || '';
    $('mediaApp').textContent = media.app === 'ru.yandex.music' ? 'Яндекс Музыка' : (media.app || '');
    if (media.art) { $('cover').style.backgroundImage = 'url(' + media.art + ')'; $('cover').classList.add('has-art'); }
    else { $('cover').style.backgroundImage = ''; $('cover').classList.remove('has-art'); }
    posBase = media.position || 0; posAt = Date.now();
    progress();
  }
  function progress() {
    if (!media.active) return;
    var pos = posBase + (media.playing ? Date.now() - posAt : 0);
    var dur = media.duration || 0;
    if (dur > 0) pos = Math.min(pos, dur);
    $('tPos').textContent = fmt(pos); $('tDur').textContent = fmt(dur);
    $('progressBar').style.width = dur > 0 ? (pos / dur * 100).toFixed(2) + '%' : '0';
  }
  setInterval(progress, 1000);
  setInterval(function () {
    if (media.active) return;
    try { var m = JSON.parse(A.mediaState()); if (!m.active && m.playing !== media.playing) { media = m; renderMedia(); } } catch (e) {}
  }, 2000);
  document.querySelectorAll('[data-media]').forEach(function (b) {
    b.addEventListener('click', function () { A.media(b.getAttribute('data-media')); });
  });
  $('openMusic').addEventListener('click', function () { A.media('open'); });

  // ---------------------------------------------------------------- navigation
  function navigate(q) {
    var r = A.navigate(q);
    if (!r) toast(T[L()].noNavi);
    return r;
  }
  // Saved places (Settings → «Запомнить здесь») give a real route; otherwise fall back to a search.
  function goPlace(kind) {
    var r = A.routeTo ? A.routeTo(kind) : '';
    if (r) return r;
    var ru = L() === 'ru';
    var q = kind === 'home' ? (ru ? 'Дом' : 'Uy') : (ru ? 'Работа' : 'Ish');
    var res = navigate(q);
    if (res) toast(ru ? 'Совет: сохраните «' + q + '» в настройках — маршрут будет строиться сразу'
                      : 'Maslahat: sozlamalarda «' + q + '» manzilini saqlang');
    return res;
  }
  function openNavi() {
    if (!A.openPackage('ru.yandex.yandexnavi') && !A.openPackage('ru.yandex.yandexmaps')) toast(T[L()].noNavi);
  }
  document.querySelectorAll('[data-nav]').forEach(function (b) {
    b.addEventListener('click', function () {
      var q = b.getAttribute('data-nav');
      if (q === 'Дом') goPlace('home'); else if (q === 'Работа') goPlace('work'); else navigate(q);
    });
  });
  $('openNavi').addEventListener('click', openNavi);
  $('openNavi2').addEventListener('click', openNavi);

  // ---------------------------------------------------------------- embedded Yandex map
  var here = null, mapShown = null, mapFollow = true;
  function mapUrl(lat, lon, zoom) {
    var ll = lon.toFixed(6) + ',' + lat.toFixed(6);
    return 'https://yandex.ru/map-widget/v1/?ll=' + ll + '&z=' + zoom + '&l=map,trf&pt=' + ll + ',pm2gnm&lang=' + (L() === 'ru' ? 'ru_RU' : 'uz_UZ');
  }
  function distM(a, b) {
    var R = 6371000, toR = Math.PI / 180;
    var dLat = (b.lat - a.lat) * toR, dLon = (b.lon - a.lon) * toR;
    var x = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(a.lat * toR) * Math.cos(b.lat * toR) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * R * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
  }
  function showMap(force) {
    var p = here || { lat: 41.2995, lon: 69.2401 };   // Tashkent until GPS answers
    if (!force && mapShown && distM(mapShown, p) < 400) return;
    mapShown = { lat: p.lat, lon: p.lon };
    $('mapFrame').src = mapUrl(p.lat, p.lon, here ? 15 : 12);
  }
  $('mapFrame').addEventListener('load', function () { $('mapOffline').hidden = true; });
  $('mapCenter').addEventListener('click', function () { showMap(true); });
  setTimeout(function () { showMap(true); }, 600);

  // ---------------------------------------------------------------- volume
  var volTimer;
  function volume(d) {
    var v = A.volume(d);
    $('volLabel').textContent = v + '%';
    clearTimeout(volTimer); volTimer = setTimeout(function () { $('volLabel').textContent = ''; }, 2500);
    return v;
  }
  document.querySelectorAll('[data-vol]').forEach(function (b) {
    b.addEventListener('click', function () { volume(+b.getAttribute('data-vol')); });
  });

  // ---------------------------------------------------------------- car comfort control
  var lastCar = null;
  function tryCar(action, value) {
    try {
      var r = A.car ? JSON.parse(A.car(action, value || 0)) : { ok: false, detail: 'no-bridge' };
      lastCar = action + ' -> ' + (r.ok ? 'ok' : 'fail') + ': ' + (r.detail || '');
      return r;
    } catch (e) { lastCar = action + ' -> error: ' + e; return { ok: false, detail: String(e) }; }
  }

  // ---------------------------------------------------------------- voice assistant
  var voiceOpen = false, speakingTimer = null, closeTimer = null;
  function setStatus(text, err) {
    $('vStatus').textContent = text;
    document.querySelector('.status').classList.toggle('err', !!err);
  }
  function renderLang() {
    document.querySelectorAll('.lang-btn, #langSeg button').forEach(function (b) {
      b.classList.toggle('on', b.getAttribute('data-lang') === L());
    });
    $('vYouLabel').textContent = t('you');
    $('vChips').innerHTML = t('tryThis').map(function (c) { return '<button data-cmd="' + c + '">' + c + '</button>'; }).join('');
    tick();
    renderMedia();
  }
  function listening(on) { document.querySelector('.avatar-wrap').classList.toggle('listening', on); }
  function talking(on) { document.querySelector('.avatar-wrap').classList.toggle('talking', on); }

  function openVoice() {
    clearTimeout(closeTimer);
    voiceOpen = true;
    $('voice').hidden = false;
    $('vHeard').textContent = '…';
    $('vAnswer').textContent = '';
    renderLang();
    setStatus(t('listening'));
    listening(true);
    A.listen(L());
  }
  function closeVoice() {
    voiceOpen = false;
    clearTimeout(closeTimer);
    $('voice').hidden = true;
    listening(false); talking(false);
    A.stopListen(); A.stopSpeaking();
    armWake(1500);
  }

  // Hands-free "Salom, Yo'ldosh" (Settings → toggle). Only while Yo'ldosh is on screen.
  var wakeTimer;
  function armWake(delay) {
    clearTimeout(wakeTimer);
    if (!settings.wake || !A.startWake) return;
    wakeTimer = setTimeout(function () {
      if (!voiceOpen && $('onboard').hidden) A.startWake(L());
    }, delay || 0);
  }
  $('micBtn').addEventListener('click', openVoice);
  $('closeVoice').addEventListener('click', closeVoice);
  $('voice').addEventListener('click', function (e) { if (e.target === $('voice')) closeVoice(); });
  $('vChips').addEventListener('click', function (e) {
    var b = e.target.closest('[data-cmd]'); if (!b) return;
    A.stopListen(); listening(false);
    handle(b.getAttribute('data-cmd'));
  });
  document.querySelectorAll('.lang-btn').forEach(function (b) {
    b.addEventListener('click', function () { setLang(b.getAttribute('data-lang')); if (voiceOpen) openVoice(); });
  });

  function say(text, then) {
    $('vAnswer').textContent = text;
    var spoken = false;
    try { spoken = A.speak(text, L()); } catch (e) {}
    clearTimeout(speakingTimer);
    if (!spoken) {
      // No TTS voice on the head unit: animate the avatar for roughly the reading time.
      talking(true);
      speakingTimer = setTimeout(function () { talking(false); }, Math.min(4000, 600 + text.length * 55));
    }
    if (then) closeTimer = setTimeout(then, Math.min(6500, 2200 + text.length * 60));
  }

  function handle(text) {
    $('vHeard').textContent = '«' + text + '»';
    setStatus(t('thinking'));
    listening(false);
    var c = window.YCommands.parse(text);
    var l = L(), x = T[l], autoClose = true, reply;
    switch (c.intent) {
      case 'play': A.media('play'); reply = media.active ? x.play : x.noMusic; break;
      case 'pause': A.media('pause'); reply = x.pause; break;
      case 'next': A.media('next'); reply = x.next; break;
      case 'prev': A.media('prev'); reply = x.prev; break;
      case 'openMusic': A.media('open'); reply = x.noMusic; break;
      case 'volUp': reply = x.volUp + ' · ' + volume(1) + '%'; break;
      case 'volDown': reply = x.volDown + ' · ' + volume(-1) + '%'; break;
      case 'navHome': reply = goPlace('home') ? x.navHome : x.noNavi; break;
      case 'navWork': reply = goPlace('work') ? x.navWork : x.noNavi; break;
      case 'navCharge': reply = navigate('Зарядка для электромобилей') ? x.navCharge : x.noNavi; break;
      case 'navFuel': reply = navigate('АЗС') ? x.navFuel : x.noNavi; break;
      case 'navTo': reply = navigate(c.arg) ? x.navTo + c.arg : x.noNavi; break;
      case 'openNavi': openNavi(); reply = x.openNavi; break;
      case 'openApp':
        var label = A.openApp(c.arg);
        var al = window.YCommands.alias(c.arg);
        if (!label && al) label = A.openApp(al);
        reply = label ? x.openApp + label : x.noApp + c.arg;
        break;
      case 'carTemp': { var r = tryCar('temp', c.value); reply = r.ok ? x.tempSet + c.value + '°' : x.carFail; break; }
      case 'carAc': { var r2 = tryCar(c.value ? 'ac_on' : 'ac_off', 0); reply = r2.ok ? (c.value ? x.acOn : x.acOff) : x.carFail; break; }
      case 'carWindow': { var r3 = tryCar(c.value ? 'window_open' : 'window_close', 0); reply = r3.ok ? (c.value ? x.winOpen : x.winClose) : x.carFail; break; }
      case 'car': reply = x.car; break;
      case 'time': reply = x.time + hhmm(new Date()); break;
      case 'date': reply = dateText(new Date()); break;
      case 'thanks': reply = x.thanks; break;
      case 'who': reply = x.who; autoClose = false; break;
      case 'help': reply = x.help; autoClose = false; break;
      case 'hello': reply = x.hello; autoClose = false; break;
      case 'close': reply = x.close; break;
      default: reply = x.unknown; autoClose = false;
    }
    setStatus('Yo‘ldosh');
    say(reply, autoClose ? closeVoice : null);
  }

  // ---------------------------------------------------------------- settings
  function setLang(l) {
    settings.lang = l;
    A.setSetting('lang', l);
    renderLang();
  }
  var TOGGLES = [
    ['wake', 'Вызов фразой «Salom, Yo‘ldosh»', 'Ассистент слушает фразу, пока Yo‘ldosh открыт на экране'],
    ['speak', 'Отвечать голосом', 'Если на магнитоле есть синтез речи'],
    ['bubble', 'Плавающая кнопка', 'Кнопка ассистента поверх навигатора и других приложений'],
    ['autostart', 'Автозапуск', 'Открывать Yo‘ldosh при включении магнитолы']
  ];
  function renderSettings() {
    $('toggles').innerHTML = TOGGLES.map(function (tg) {
      var on = !!settings[tg[0]];
      return '<button class="tg' + (on ? ' on' : '') + '" role="switch" aria-checked="' + on + '" data-tg="' + tg[0] + '">' +
        '<span><b>' + tg[1] + '</b><small>' + tg[2] + '</small></span><span class="sw"><i></i></span></button>';
    }).join('');
    var lk = settings.lastKey, wk = settings.wheelKey;
    $('keyInfo').textContent = (wk > 0 ? 'Назначена кнопка с кодом ' + wk + '. ' : '') +
      (lk > 0 ? 'Последняя нажатая: ' + lk : 'Включите «Спецвозможности» в разрешениях и нажмите кнопку на руле');
    var chrome = (navigator.userAgent.match(/Chrome\/(\d+)/) || [])[1] || '?';
    $('version').textContent = 'Версия ' + (settings.version || '') + ' · голос: ' + (settings.ttsEngine || '—') +
      ' · WebView ' + chrome + ' · 3D: ' + (has3d ? 'да' : 'нет') + (errors.length ? ' · ошибки: ' + errors.join(' | ') : '');
  }
  $('toggles').addEventListener('click', function (e) {
    var b = e.target.closest('[data-tg]'); if (!b) return;
    var k = b.getAttribute('data-tg');
    settings[k] = !settings[k];
    A.setSetting(k, String(settings[k]));
    if (k === 'wake') { if (settings.wake) armWake(300); else if (A.stopWake) A.stopWake(); }
    renderSettings();
  });
  function renderPlaces() {
    $('placeHome').textContent = settings.home ? 'Дом сохранён · обновить' : 'Здесь мой дом';
    $('placeWork').textContent = settings.work ? 'Работа сохранена · обновить' : 'Здесь моя работа';
  }
  function savePlace(kind) {
    if (!perms.location) { A.requestPerm('location'); toast('Разрешите геолокацию и нажмите ещё раз'); return; }
    if (A.saveHere && A.saveHere(kind)) {
      settings[kind] = true;
      toast(kind === 'home' ? 'Дом сохранён. Скажите «Поехали домой»' : 'Работа сохранена. Скажите «На работу»');
    } else toast('Не удалось определить место. Подождите, пока появится GPS, и попробуйте снова');
    renderPlaces();
  }
  $('placeHome').addEventListener('click', function () { savePlace('home'); });
  $('placeWork').addEventListener('click', function () { savePlace('work'); });
  $('langSeg').addEventListener('click', function (e) {
    var b = e.target.closest('[data-lang]'); if (b) setLang(b.getAttribute('data-lang'));
  });
  $('openSettings').addEventListener('click', function () {
    try { settings = JSON.parse(A.settings()); } catch (e) {}
    renderSettings(); renderPlaces(); renderLang();
    $('settings').hidden = false;
  });
  $('closeSettings').addEventListener('click', function () { $('settings').hidden = true; });
  $('settings').addEventListener('click', function (e) { if (e.target === $('settings')) $('settings').hidden = true; });
  $('useLastKey').addEventListener('click', function () {
    try { settings = JSON.parse(A.settings()); } catch (e) {}
    if (settings.lastKey > 0) {
      settings.wheelKey = settings.lastKey;
      A.setSetting('wheelKey', String(settings.lastKey));
      toast('Кнопка ' + settings.lastKey + ' теперь вызывает ассистента');
    } else toast('Сначала нажмите кнопку на руле');
    renderSettings();
  });
  $('reopenPerms').addEventListener('click', function () { $('settings').hidden = true; showOnboarding(true); });
  function buildReport() {
    var r = { app: settings.version, when: new Date().toISOString() };
    try { r.device = JSON.parse(A.deviceInfo ? A.deviceInfo() : '{}'); } catch (e) { r.device = { err: String(e) }; }
    r.webview = (navigator.userAgent.match(/Chrome\/(\d+)/) || [])[1] || '?';
    r.ua = navigator.userAgent;
    r.screen = window.innerWidth + 'x' + window.innerHeight;
    r.lang = settings.lang;
    r.ttsEngine = settings.ttsEngine;
    r.has3d = has3d;
    r.perms = perms;
    try { r.installedApps = JSON.parse(A.installed ? A.installed() : '[]'); } catch (e) {}
    try { r.media = JSON.parse(A.mediaState ? A.mediaState() : '{}'); } catch (e) {}
    try { r.car = JSON.parse(A.carProbe ? A.carProbe() : '{}'); } catch (e) { r.car = { err: String(e) }; }
    if (lastCar) r.lastCarCommand = lastCar;
    if (errors.length) r.jsErrors = errors;
    return JSON.stringify(r, null, 2);
  }

  $('carDiag').addEventListener('click', function () {
    var out = $('carOut');
    out.hidden = !out.hidden;
    if (!out.hidden) {
      out.textContent = 'Собираю данные…';
      setTimeout(function () { try { out.textContent = A.diagnostics(); } catch (e) { out.textContent = buildReport(); } }, 50);
    }
  });
  $('diagShare').addEventListener('click', function () {
    if (A.shareDiagnostics) A.shareDiagnostics();
    else toast('Отправка недоступна');
  });
  $('diagCopy').addEventListener('click', function () {
    var ok = A.copyDiagnostics && A.copyDiagnostics();
    toast(ok ? 'Скопировано — вставьте в чат разработчику' : 'Не удалось скопировать');
  });
  $('diagTest').addEventListener('click', function () {
    var res;
    try { res = A.carTest ? JSON.parse(A.carTest(22)) : { ok: false, detail: 'нет моста' }; }
    catch (e) { res = { ok: false, detail: String(e) }; }
    lastCar = 'test temp 22 -> ' + (res.ok ? 'ok' : 'fail') + ': ' + (res.detail || '');
    toast(res.ok ? 'Климат выставлен на 22° ✓' : 'Не сработало: ' + (res.detail || '').slice(0, 80));
    var out = $('carOut');
    if (!out.hidden) { try { out.textContent = A.diagnostics(); } catch (e) {} }
  });

  // ---------------------------------------------------------------- onboarding / permissions
  var PERMS = [
    ['mic', 'Микрофон', 'Чтобы слышать «Salom, Yo‘ldosh» и ваши команды', 'Разрешить'],
    ['location', 'Геолокация', 'Маршруты и ближайшие зарядки', 'Разрешить'],
    ['overlay', 'Поверх других приложений', 'Кнопка ассистента видна даже в навигаторе', 'Открыть настройки'],
    ['notif', 'Доступ к уведомлениям', 'Показывать трек и обложку из Яндекс Музыки', 'Открыть настройки'],
    ['a11y', 'Спецвозможности', 'Кнопка голоса на руле', 'Открыть настройки'],
    ['battery', 'Работа в фоне', 'Чтобы система не выключала ассистента', 'Разрешить'],
    ['autostart', 'Автозапуск', 'Включаться вместе с магнитолой', 'Включить']
  ];
  function renderOnboarding() {
    var next = -1, done = 0;
    PERMS.forEach(function (p, i) { if (perms[p[0]]) done++; else if (next < 0) next = i; });
    $('obList').innerHTML = PERMS.map(function (p, i) {
      var ok = !!perms[p[0]];
      return '<div class="perm' + (ok ? ' done' : '') + (i === next ? ' next' : '') + '">' +
        '<div class="num">' + (ok ? '✓' : (i + 1)) + '</div>' +
        '<div class="txt"><b>' + p[1] + '</b><small>' + p[2] + '</small></div>' +
        '<button class="act" data-perm="' + p[0] + '"' + (ok ? ' disabled' : '') + '>' + (ok ? 'Готово' : p[3]) + '</button></div>';
    }).join('');
    $('obCount').textContent = 'Готово ' + done + ' из ' + PERMS.length;
    $('obBar').style.width = (done / PERMS.length * 100) + '%';
    $('obNext').textContent = next < 0 ? 'Начать' : 'Разрешить следующее';
    $('obNext').setAttribute('data-next', next < 0 ? '' : PERMS[next][0]);
  }
  function showOnboarding(on) {
    $('onboard').hidden = !on;
    $('home').hidden = on;
    if (on) renderOnboarding();
  }
  function finishOnboarding() {
    settings.onboarded = true;
    A.setSetting('onboarded', 'true');
    showOnboarding(false);
    armWake(500);
  }
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-perm]'); if (!b || b.disabled) return;
    A.requestPerm(b.getAttribute('data-perm'));
  });
  $('obNext').addEventListener('click', function () {
    var k = $('obNext').getAttribute('data-next');
    if (k) A.requestPerm(k); else finishOnboarding();
  });
  $('obSkip').addEventListener('click', finishOnboarding);

  // ---------------------------------------------------------------- events from Kotlin
  window.onNative = function (event, data) {
    switch (event) {
      case 'perms':
        perms = data || {};
        if (!$('onboard').hidden) renderOnboarding();
        renderMedia();
        break;
      case 'media':
        media = data || { access: false, active: false };
        renderMedia();
        break;
      case 'speaking':
        clearTimeout(speakingTimer);
        talking(!!data);
        break;
      case 'listen':
        if ($('onboard').hidden) openVoice();
        break;
      case 'location':
        here = { lat: data.lat, lon: data.lon };
        showMap(false);
        break;
      case 'resumed':
        if (!voiceOpen) armWake(800);
        break;
      case 'voice':
        if (!voiceOpen) return;
        var st = data.state || '';
        if (st === 'partial') { $('vHeard').textContent = data.text + '…'; }
        else if (st === 'final') { handle(data.text); }
        else if (st === 'listening') { setStatus(t('listening')); listening(true); }
        else if (st === 'loading') { setStatus(t('loading')); }
        else if (st === 'timeout') { setStatus(t('timeout'), true); listening(false); }
        else if (st === 'nomic') { setStatus(t('nomic'), true); listening(false); $('vAnswer').innerHTML = '<button class="link-btn" data-perm="mic">Разрешить микрофон</button>'; }
        else if (st.indexOf('error') === 0) { setStatus(t('error'), true); listening(false); $('vAnswer').textContent = st.slice(6); }
        break;
    }
  };
  window.onBack = function () {
    if (!$('voice').hidden) closeVoice();
    else if (!$('settings').hidden) $('settings').hidden = true;
  };

  // ---------------------------------------------------------------- start
  renderLang();
  showOnboarding(!settings.onboarded);
  try { media = JSON.parse(A.mediaState()); } catch (e) {}
  renderMedia();
  A.ready();
  armWake(3000);
})();
