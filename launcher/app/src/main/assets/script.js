// ═══════════ إعدادات قابلة للتعديل ═══════════
// غيّر هذا إلى false لعرض الأرقام اللاتينية (0-9) بدلاً من الأرقام العربية (٠-٩)
var USE_ARABIC_NUMERALS = true;
var BG_INTERVAL_MS = 90 * 1000;     // تبديل صورة الخلفية بتلاشي ناعم كل دقيقة ونصف (90 ثانية)
var BG_FADE_MS = 2500;                  // مدة التلاشي بين الصورتين (نفس transition في style.css)
var MATCH_ROTATE_MS = 10 * 1000;        // تبديل المباراة المعروضة كل 10 ثوانٍ
var MATCH_FADE_MS = 350;                // مدة تلاشي نص المباراة (نفس transition في style.css)
var PRAYER_ALERT_ENABLED = true;        // تفعيل تنبيهات الأذان والإقامة التفاعلية
var MATCHDAY_MODE_ENABLED = true;       // تفعيل وضع يوم المباراة والتحديثات الحية
var SELECTED_FONT = 'thmanyah';
var FONT_SCALE = 'normal';
var BG_DIM = 'medium';
var USE_24H_CLOCK = false;
var SHOW_SECONDS = false;
var GREETING_TEXT = '';
var NEWS_ROTATE_MS = 10000;

// ═══════════ إعدادات تأتي من ApiConfig.kt (لا تعدّلها هنا) ═══════════
// القيم التالية افتراضية فقط (تظهر في preview.html). في التطبيق يدفع كود Kotlin القيم الحقيقية
// من ApiConfig.kt عبر setConfig() فور تحميل الصفحة: اسم المدينة، اسم الفريق، المنطقة الزمنية.
var CITY_NAME = 'جدة';
var TEAM_NAME = 'الهلال';
var TEAM2_NAME = '';
var TZ_NAME = 'Asia/Riyadh';              // اسم المنطقة الزمنية (IANA)؛ يتعامل مع التوقيت الصيفي تلقائيًا
var TZ_FALLBACK_MS = 3 * 60 * 60 * 1000; // إزاحة احتياطية تُستخدم فقط إن لم يدعم الـ WebView أسماء المناطق

// ═══════════ أدوات مشتركة ═══════════
var ARABIC_DIGITS = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];
var WEEKDAYS = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
var MONTHS = [
  'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
  'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'
];
var HIJRI_MONTHS = [
  'محرم', 'صفر', 'ربيع الأول', 'ربيع الآخر', 'جمادى الأولى', 'جمادى الآخرة',
  'رجب', 'شعبان', 'رمضان', 'شوال', 'ذو القعدة', 'ذو الحجة'
];

function $(id) { return document.getElementById(id); }

function toLocaleDigits(input) {
  var str = String(input);
  if (!USE_ARABIC_NUMERALS) return str;
  return str.replace(/[0-9]/g, function (d) { return ARABIC_DIGITS[parseInt(d, 10)]; });
}

function pad2(n) { return n < 10 ? '0' + n : '' + n; }

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
  });
}

// يكتب النص في العنصر فقط إن تغيّر فعلًا (لا إعادة رسم بلا داعٍ — أخف على التلفزيون)
var lastText = {};
function setText(id, text) {
  if (lastText[id] === text) return;
  lastText[id] = text;
  $(id).textContent = text;
}

// نفس الفكرة لكن لسطر فيه فواصل "·" (تُرسم في span له هامش حتى لا تلتصق بالأرقام)
// ملاحظة: المدخلات هنا نصوص من الكود نفسه (أرقام وأسماء عربية) — ما يأتي من الـ API يمر عبر esc().
var lastHtml = {};
function setDotted(id, parts) {
  var html = parts.join('<span class="sep">·</span>');
  if (lastHtml[id] === html) return;
  lastHtml[id] = html;
  $(id).innerHTML = html;
}

// الوقت الحالي (epoch ms). دالة منفصلة عمدًا ليمكن استبدالها في preview.html لمحاكاة أوقات أخرى.
function nowMs() { return Date.now(); }

// إزاحة توقيت المدينة (بالميلي ثانية عن UTC) عند اللحظة ms، مع احتساب التوقيت الصيفي إن وُجد.
// نستخدم Intl بالمنطقة الزمنية المحددة؛ وإن لم يدعمها الـ WebView نرجع للإزاحة الاحتياطية.
var tzFmt = null;
function tzOffsetAt(ms) {
  if (!TZ_NAME) return TZ_FALLBACK_MS;
  try {
    if (!tzFmt) {
      tzFmt = new Intl.DateTimeFormat('en-US', {
        timeZone: TZ_NAME, hour12: false,
        year: 'numeric', month: 'numeric', day: 'numeric',
        hour: 'numeric', minute: 'numeric', second: 'numeric'
      });
    }
    var parts = tzFmt.formatToParts(new Date(ms));
    var v = {};
    for (var i = 0; i < parts.length; i++) v[parts[i].type] = parseInt(parts[i].value, 10);
    if (!v.year || !v.month || !v.day) throw new Error('bad tz parts');
    // بعض المحركات تكتب منتصف الليل "24" بدل "0"
    var asUtc = Date.UTC(v.year, v.month - 1, v.day, (v.hour || 0) % 24, v.minute || 0, v.second || 0);
    return asUtc - Math.floor(ms / 1000) * 1000;
  } catch (e) {
    TZ_NAME = ''; // لا نكرر محاولة فاشلة كل ثانية
    return TZ_FALLBACK_MS;
  }
}

// مكوّنات التاريخ والوقت بتوقيت المدينة مهما كان توقيت الجهاز
function cityParts(ms) {
  var d = new Date(ms + tzOffsetAt(ms));
  return {
    y: d.getUTCFullYear(), mo: d.getUTCMonth(), d: d.getUTCDate(), wd: d.getUTCDay(),
    h: d.getUTCHours(), mi: d.getUTCMinutes(), s: d.getUTCSeconds()
  };
}

// رقم اليوم بتوقيت المدينة (للمقارنة بين الأيام: اليوم / بكرة / عدد الأيام المتبقية)
function dayNumber(ms) { return Math.floor((ms + tzOffsetAt(ms)) / 86400000); }

// "٤:٢١ ص" — نظام 12 ساعة بدون صفر بادئ
function fmtTime12(ms) {
  var p = cityParts(ms);
  return toLocaleDigits(p.h % 12 || 12) + ':' + toLocaleDigits(pad2(p.mi)) + ' ' + (p.h >= 12 ? 'م' : 'ص');
}

// مفرد / مثنى / 3-10 جمع / 11+ مفرد — مثل: ساعة، ساعتين، ٣ ساعات، ١١ ساعة
function unitText(n, one, two, few, many) {
  if (n === 1) return one;
  if (n === 2) return two;
  return toLocaleDigits(n) + ' ' + (n >= 3 && n <= 10 ? few : many);
}

// التاريخ الهجري (أم القرى) من محرك Intl المدمج في الـ WebView؛ إن لم يتوفر نُخفي الهجري بدل عرض تاريخ خاطئ
function hijriLabel(ms) {
  try {
    var fmt = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura-nu-latn', {
      day: 'numeric', month: 'numeric', year: 'numeric', timeZone: 'UTC'
    });
    var parts = fmt.formatToParts(new Date(ms + tzOffsetAt(ms)));
    var day = 0, month = 0, year = 0;
    for (var i = 0; i < parts.length; i++) {
      if (parts[i].type === 'day') day = parseInt(parts[i].value, 10);
      else if (parts[i].type === 'month') month = parseInt(parts[i].value, 10);
      else if (parts[i].type === 'year') year = parseInt(parts[i].value, 10);
    }
    if (!day || !year || month < 1 || month > 12) return '';
    return toLocaleDigits(day) + ' ' + HIJRI_MONTHS[month - 1] + ' ' + toLocaleDigits(year) + ' هـ';
  } catch (e) {
    return '';
  }
}

// ═══════════ الساعة والتاريخ ═══════════
var lastDayNumber = null;

function refreshDate(now) {
  var p = cityParts(now);
  setText('weekday', WEEKDAYS[p.wd]);
  var greg = toLocaleDigits(p.d) + ' ' + MONTHS[p.mo] + ' ' + toLocaleDigits(p.y);
  var hijri = hijriLabel(now);
  setDotted('date-label', hijri ? [greg, hijri] : [greg]);
}

// تُستدعى كل ثانية لكنها لا تلمس الصفحة إلا عند تغيّر النص (دقيقة جديدة، يوم جديد، ...)
function tick() {
  var now = nowMs();
  var p = cityParts(now);

  if (USE_24H_CLOCK) {
    setText('clock-h', toLocaleDigits(pad2(p.h)));
    setText('clock-m', toLocaleDigits(pad2(p.mi)));
    setText('clock-period', '');
  } else {
    setText('clock-h', toLocaleDigits(p.h % 12 || 12));
    setText('clock-m', toLocaleDigits(pad2(p.mi)));
    setText('clock-period', p.h >= 12 ? 'م' : 'ص');
  }

  var sEl = $('clock-s');
  if (sEl) {
    if (SHOW_SECONDS) {
      sEl.style.display = 'inline';
      sEl.textContent = ':' + toLocaleDigits(pad2(p.s));
    } else {
      sEl.style.display = 'none';
    }
  }

  var dn = dayNumber(now);
  if (dn !== lastDayNumber) {
    lastDayNumber = dn;
    refreshDate(now);
    renderMatch(); // "اليوم / بكرة" في المباراة تعتمد على اليوم
  }
  renderPrayer(now);
  renderRamadan(now);
}

// ═══════════ عدّاد الصلاة القادمة والإقامة (محلي بالكامل) ═══════════
// Kotlin يدفع أوقات اليوم وبكرة كلحظات مطلقة (epoch ms) — والعدّ التنازلي يحسبه JavaScript هنا كل ثانية،
// فيستمر بالعمل حتى لو انقطع الإنترنت. بعد العشاء تصير "القادمة" هي فجر بكرة تلقائيًا.
var PRAYER_NAMES = { fajr: 'الفجر', dhuhr: 'الظهر', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' };
// فترات الإقامة الافتراضية بالدقائق (المعتمدة رسميًا في مساجد السعودية: الفجر 25د، المغرب 10د، البقية 20د)
var IQAMAH_MINUTES = { fajr: 25, dhuhr: 20, asr: 20, maghrib: 10, isha: 20 };
var prayerEvents = null; // null = لم تصل بيانات بعد (جاري التحميل)
var DEFAULT_JEDDAH_PRAYERS = { fajr: '05:06', dhuhr: '12:07', asr: '15:26', maghrib: '17:51', isha: '19:21' };
var todayPrayers = DEFAULT_JEDDAH_PRAYERS; // أوقات الصلوات الخمس اليومية بدقة

function updatePrayers(data) {
  var list = (data && data.events) || [];
  var clean = [];
  for (var i = 0; i < list.length; i++) {
    var e = list[i];
    if (e && PRAYER_NAMES[e.key] && typeof e.ts === 'number' && isFinite(e.ts)) {
      clean.push({ key: e.key, ts: e.ts });
    }
  }
  clean.sort(function (a, b) { return a.ts - b.ts; });
  prayerEvents = clean;
  if (data && data.today) {
    todayPrayers = data.today;
  } else if (!todayPrayers && clean.length) {
    // احتياط: استخراج أوقات صلوات اليوم من القائمة إن لم تُمرر اليومية
    var todayDn = dayNumber(nowMs());
    var synth = {};
    for (var j = 0; j < clean.length; j++) {
      var ev = clean[j];
      if (dayNumber(ev.ts) === todayDn && !synth[ev.key]) {
        var cp = cityParts(ev.ts);
        synth[ev.key] = pad2(cp.h) + ':' + pad2(cp.mi);
      }
    }
    if (synth.fajr) todayPrayers = synth;
  }
  renderPrayer(nowMs());
}

// "بعد ٥ ساعات و٥٠ دقيقة" — الدقائق تُقرَّب للأعلى (آخر دقيقة تظهر "بعد دقيقة" ثم تنتقل للصلاة التالية)
function untilText(ms) {
  var totalMin = Math.max(1, Math.ceil(ms / 60000));
  var h = Math.floor(totalMin / 60);
  var m = totalMin % 60;
  var parts = [];
  if (h > 0) parts.push(unitText(h, 'ساعة', 'ساعتين', 'ساعات', 'ساعة'));
  if (m > 0) parts.push(unitText(m, 'دقيقة', 'دقيقتين', 'دقائق', 'دقيقة'));
  return 'بعد ' + parts.join(' و');
}

// تحويل توقيت "15:28" أو "17:55" إلى نظام 12 ساعة "٣:٢٨" و "٥:٥٥" بدون أصفار بادئة
function to12Hour(hhmm) {
  if (!hhmm) return '';
  var parts = String(hhmm).split(':');
  if (parts.length < 2) return hhmm;
  var h = parseInt(parts[0], 10);
  var m = parts[1];
  if (isNaN(h)) return hhmm;
  var h12 = h % 12 || 12;
  return toLocaleDigits(h12) + ':' + toLocaleDigits(m);
}

// حساب وقت الإقامة بصيغة 12 ساعة بناءً على وقت الأذان وفترة الانتظار
function getIqamah12(prayerKey, azanTimeStr) {
  if (!azanTimeStr) return '';
  var parts = String(azanTimeStr).split(':');
  if (parts.length < 2) return '';
  var h = parseInt(parts[0], 10);
  var m = parseInt(parts[1], 10);
  if (isNaN(h) || isNaN(m)) return '';
  var offset = (IQAMAH_MINUTES && IQAMAH_MINUTES[prayerKey]) || 20;
  var total = h * 60 + m + offset;
  var iqH = Math.floor(total / 60) % 24;
  var iqM = total % 60;
  var period = iqH >= 12 ? 'م' : 'ص';
  var h12 = iqH % 12 || 12;
  return toLocaleDigits(h12) + ':' + toLocaleDigits(pad2(iqM));
}

function to12HourWithPeriod(hhmm) {
  if (!hhmm) return '';
  var parts = String(hhmm).split(':');
  if (parts.length < 2) return hhmm;
  var h = parseInt(parts[0], 10);
  var m = parts[1];
  if (isNaN(h)) return hhmm;
  var period = h >= 12 ? 'م' : 'ص';
  var h12 = h % 12 || 12;
  return toLocaleDigits(h12) + ':' + toLocaleDigits(m) + ' ' + period;
}

function renderPrayersBar(activeKey) {
  var el = $('prayers-bar');
  if (!el || !todayPrayers) return;
  var keys = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
  var shortNames = { fajr: 'الفجر', dhuhr: 'الظهر', asr: 'العصر', maghrib: 'المغرب', isha: 'العشاء' };
  var items = [];
  for (var i = 0; i < keys.length; i++) {
    var k = keys[i];
    var timeVal = todayPrayers[k];
    if (!timeVal) continue;
    var isActive = (k === activeKey);
    var azan12 = to12HourWithPeriod(timeVal);
    var iqVal = getIqamah12(k, timeVal);
    items.push(
      '<div class="p-item' + (isActive ? ' active' : '') + '">' +
        '<span class="p-name">' + shortNames[k] + '</span>' +
        '<span class="p-azan">' + azan12 + '</span>' +
        '<span class="p-iqamah">' + (iqVal ? 'إقامة ' + iqVal : '') + '</span>' +
      '</div>'
    );
  }
  el.innerHTML = items.join('');
}

function updatePrayerAlert(title, desc, icon, show) {
  var banner = $('prayer-alert-banner');
  if (!banner) return;
  if (!show || !PRAYER_ALERT_ENABLED) {
    banner.style.display = 'none';
    return;
  }
  if ($('alert-title')) $('alert-title').textContent = title;
  if ($('alert-desc')) $('alert-desc').textContent = desc;
  if ($('alert-icon')) $('alert-icon').textContent = icon || '🕌';
  banner.style.display = 'flex';
}

function renderPrayer(now) {
  if (prayerEvents === null) {
    if ($('prayer-label')) $('prayer-label').textContent = 'مواقيت الصلاة والإقامة — ' + CITY_NAME;
    setText('prayer-sub', 'جاري التحميل…');
    renderPrayersBar('fajr');
    updatePrayerAlert('', '', '', false);
    return;
  }

  // 1. هل نحن بين أذان الصلاة الحالية وإقامتها، أو أثناء إقامة الصلاة؟
  var currentActive = null;
  var currentStatus = ''; // 'between' أو 'praying'
  for (var i = 0; i < prayerEvents.length; i++) {
    var ev = prayerEvents[i];
    var iqOffsetMs = ((IQAMAH_MINUTES && IQAMAH_MINUTES[ev.key]) || 20) * 60000;
    var iqTs = ev.ts + iqOffsetMs;
    var endTs = iqTs + 15 * 60000; // 15 دقيقة نافذة أداء الصلاة
    if (now >= ev.ts && now < iqTs) {
      currentActive = ev;
      currentStatus = 'between';
      break;
    } else if (now >= iqTs && now < endTs) {
      currentActive = ev;
      currentStatus = 'praying';
      break;
    }
  }

  if (currentActive) {
    var pKey = currentActive.key;
    var pName = PRAYER_NAMES[pKey];
    var offsetMs = ((IQAMAH_MINUTES && IQAMAH_MINUTES[pKey]) || 20) * 60000;
    var targetIqTs = currentActive.ts + offsetMs;

    if ($('prayer-label')) $('prayer-label').textContent = 'مواقيت الصلاة والإقامة — ' + CITY_NAME;
    renderPrayersBar(pKey);

    if (currentStatus === 'between') {
      var minToIq = Math.max(1, Math.ceil((targetIqTs - now) / 60000));
      setDotted('prayer-sub', [
        'أذان ' + pName + ' 🕌',
        'الإقامة بعد ' + unitText(minToIq, 'دقيقة', 'دقيقتين', 'دقائق', 'دقيقة')
      ]);
      updatePrayerAlert(
        'حان الآن موعد أذان ' + pName + ' 🕌',
        'الإقامة بعد ' + unitText(minToIq, 'دقيقة', 'دقيقتين', 'دقائق', 'دقيقة') + ' · توقيت ' + CITY_NAME,
        '🕌',
        true
      );
    } else {
      setDotted('prayer-sub', ['تقام الصلاة الآن 🕋', 'تقبل الله طاعتكم']);
      updatePrayerAlert(
        'تقام الآن صلاة ' + pName + ' 🕋',
        'تقبل الله طاعتكم وأصلح بالكم · أقم صلاتك',
        '🕋',
        true
      );
    }
    return;
  }

  // 2. قبل الأذان: البحث عن الصلاة القادمة
  var next = null;
  for (var j = 0; j < prayerEvents.length; j++) {
    if (prayerEvents[j].ts > now) { next = prayerEvents[j]; break; }
  }

  if (!next) {
    if ($('prayer-label')) $('prayer-label').textContent = 'مواقيت الصلاة والإقامة — ' + CITY_NAME;
    renderPrayersBar('fajr');
    setText('prayer-sub', 'أوقات الصلاة غير متاحة حالياً');
    updatePrayerAlert('', '', '', false);
    return;
  }

  var diffToNext = next.ts - now;
  if (diffToNext > 0 && diffToNext <= 10 * 60000) {
    updatePrayerAlert(
      'اقترب موعد أذان ' + PRAYER_NAMES[next.key] + ' ⏳',
      'متبقي ' + untilText(diffToNext).replace('بعد ', '') + ' حتى رفع الأذان · توقيت ' + CITY_NAME,
      '⏳',
      true
    );
  } else {
    updatePrayerAlert('', '', '', false);
  }

  var tomorrow = dayNumber(next.ts) - dayNumber(now) === 1;
  var nextOffset = ((IQAMAH_MINUTES && IQAMAH_MINUTES[next.key]) || 20) * 60000;
  var nextIqTs = next.ts + nextOffset;

  if ($('prayer-label')) $('prayer-label').textContent = 'مواقيت الصلاة والإقامة — ' + CITY_NAME;
  renderPrayersBar(next.key);
  var subParts = [
    'الصلاة القادمة: ' + PRAYER_NAMES[next.key] + (tomorrow ? ' غداً' : '') + ' بعد ' + untilText(next.ts - now),
    'إقامة ' + fmtTime12(nextIqTs)
  ];
  setDotted('prayer-sub', subParts);
}

// ═══════════ عدّاد رمضان ═══════════
var ramadanTarget = null; // لحظة بداية رمضان (epoch ms)

function updateRamadan(data) {
  if (!data) return;
  if (typeof data.target === 'number' && data.target > 0) {
    ramadanTarget = data.target;
  } else if (typeof data.days === 'number') { // توافق مع نسخة Kotlin القديمة بدون target
    ramadanTarget = nowMs() + (((data.days * 24) + (data.hours || 0)) * 60 + (data.minutes || 0)) * 60000;
  }
  renderRamadan(nowMs());
}

function renderRamadan(now) {
  if (ramadanTarget === null) {
    setText('ramadan-value', '—');
    setText('ramadan-unit', '');
    setText('ramadan-sub', 'جاري التحميل…');
    return;
  }
  // عدد الأيام بفرق التقويم (مثل المرجع: ١١٣ يومًا)، ويتغيّر عند منتصف الليل
  var n = Math.max(0, dayNumber(ramadanTarget) - dayNumber(now));
  var value, unit = '';
  if (n === 0) value = 'اليوم';
  else if (n === 1) value = 'بكرة';
  else if (n === 2) value = 'يومان';
  else {
    value = toLocaleDigits(n);
    unit = n <= 10 ? 'أيام' : 'يومًا';
  }
  var t = cityParts(ramadanTarget);
  setText('ramadan-value', value);
  setText('ramadan-unit', unit);
  setText('ramadan-sub', 'يبدأ ' + WEEKDAYS[t.wd] + '، ' + toLocaleDigits(t.d) + ' ' + MONTHS[t.mo] + ' تقريبًا');
}

// ═══════════ الطقس ═══════════
function iconId(code) {
  var c = String(code || '');
  var night = c.charAt(2) === 'n';
  switch (c.slice(0, 2)) {
    case '01': return night ? 'i-clear-n' : 'i-clear-d';
    case '02': return night ? 'i-few-n' : 'i-few-d';
    case '03': case '04': return 'i-cloud';
    case '09': case '10': return 'i-rain';
    case '11': return 'i-thunder';
    case '13': return 'i-snow';
    case '50': return 'i-mist';
    default: return 'i-cloud';
  }
}

var lastForecastSig = '';

function updateWeather(data) {
  if (!data) return;
  var iconEl = $('weather-icon');
  if (iconEl) iconEl.setAttribute('href', '#' + iconId(data.icon));
  setText('weather-temp', toLocaleDigits(data.temp) + '°');
  setText('weather-desc', data.description || 'صافٍ ومعتدل');
  var cityEl = $('weather-label');
  if (cityEl) cityEl.textContent = '📍 ' + (CITY_NAME || 'جدة');

  var daily = data.daily || [];
  var sig = JSON.stringify(daily);
  if (sig === lastForecastSig) return;
  lastForecastSig = sig;

  var html = '';
  for (var i = 0; i < daily.length && i < 5; i++) {
    var day = daily[i];
    html +=
      '<div class="fday">' +
        '<span>' + esc(i === 0 ? 'اليوم' : day.day) + '</span>' +
        '<svg class="wicon" viewBox="0 0 48 48" aria-hidden="true"><use href="#' + iconId(String(day.icon || '').slice(0, 2) + 'd') + '"></use></svg>' +
        '<span class="hi">' + toLocaleDigits(day.max) + '°</span>' +
        '<span class="lo">' + toLocaleDigits(day.min) + '°</span>' +
      '</div>';
  }
  var fEl = $('weather-forecast');
  if (fEl) fEl.innerHTML = html;
}

// ═══════════ مباريات الفريق (تتبدل على الخمس كل 10 ثوانٍ) ═══════════
// أسماء الأندية بالعربية (الـ API يرسلها بالإنجليزية). الترتيب مهم: الأخص أولًا. ما لا يوجد هنا يُعرض بالإنجليزية.
var TEAM_NAMES = [
  // أندية المملكة العربية السعودية والخليج وآسيا
  ['al hilal', 'الهلال'], ['al-hilal', 'الهلال'], ['al nassr', 'النصر'], ['al-nassr', 'النصر'],
  ['al ittihad', 'الاتحاد'], ['al-ittihad', 'الاتحاد'], ['al ahli', 'الأهلي'], ['al-ahli', 'الأهلي'],
  ['al shabab', 'الشباب'], ['al-shabab', 'الشباب'], ['al ettifaq', 'الاتفاق'], ['al-ettifaq', 'الاتفاق'],
  ['al taawoun', 'التعاون'], ['al-taawoun', 'التعاون'], ['al fateh', 'الفتح'], ['al-fateh', 'الفتح'],
  ['al fayha', 'الفيحاء'], ['al-fayha', 'الفيحاء'], ['al raed', 'الرائد'], ['al-raed', 'الرائد'],
  ['al khaleej', 'الخليج'], ['al-khaleej', 'الخليج'], ['al wehda', 'الوحدة'], ['al-wehda', 'الوحدة'],
  ['al okhdood', 'الأخدود'], ['al-okhdood', 'الأخدود'], ['al riyadh', 'الرياض'], ['al-riyadh', 'الرياض'],
  ['al hazem', 'الحزم'], ['al-hazem', 'الحزم'], ['al tai', 'الطائي'], ['al-tai', 'الطائي'],
  ['al qadsiah', 'القادسية'], ['al-qadsiah', 'القادسية'], ['al kholood', 'الخلود'], ['al-kholood', 'الخلود'],
  ['al orobah', 'العروبة'], ['al-orobah', 'العروبة'], ['al diriyah', 'الدرعية'], ['al-diriyah', 'الدرعية'],
  ['neom', 'نيوم'], ['abha', 'أبها'], ['al faisaly', 'الفيصلي'], ['al-faisaly', 'الفيصلي'],
  ['al batin', 'الباطن'], ['al-batin', 'الباطن'], ['al adalah', 'العدالة'],
  ['al sadd', 'السد'], ['al-sadd', 'السد'], ['al duhail', 'الدحيل'], ['al-duhail', 'الدحيل'],
  ['al gharafa', 'الغرافة'], ['al-gharafa', 'الغرافة'], ['al rayyan', 'الريان'], ['al-rayyan', 'الريان'],
  ['al shamal', 'الشمال'], ['al-shamal', 'الشمال'], ['al ain', 'العين'], ['al-ain', 'العين'],
  ['shabab al ahli', 'شباب الأهلي'], ['shabab al-ahli', 'شباب الأهلي'], ['al wasl', 'الوصل'], ['al-wasl', 'الوصل'],
  ['al sharjah', 'الشارقة'], ['al-sharjah', 'الشارقة'],
  ['persepolis', 'بيرسبوليس'], ['esteghlal', 'استقلال طهران'], ['sepahan', 'سباهان'],
  ['pakhtakor', 'باختاكور'], ['nasaf', 'ناساف'], ['neftchi', 'نيفتشي فرغانة'],
  ['kawasaki', 'كاواساكي فرونتال'], ['yokohama', 'يوكوهاما مارينوس'], ['vissel kobe', 'فيسيل كوبي'],
  ['ulsan', 'أولسان هيونداي'], ['pohang', 'بوهانغ ستيلرز'], ['gwangju', 'غوانغجو'],
  ['buriram', 'بوريرام يونايتد'], ['johor', 'جوهور دار التعظيم'],

  // الأندية العالمية
  ['chelsea', 'تشيلسي'], ['manchester united', 'مانشستر يونايتد'], ['manchester city', 'مانشستر سيتي'], ['tottenham', 'توتنهام'],
  ['arsenal', 'آرسنال'], ['liverpool', 'ليفربول'], ['newcastle', 'نيوكاسل'], ['aston villa', 'أستون فيلا'],
  ['real madrid', 'ريال مدريد'], ['barcelona', 'برشلونة'], ['atletico', 'أتلتيكو مدريد'],
  ['bayern', 'بايرن ميونخ'], ['dortmund', 'بوروسيا دورتموند'],
  ['paris saint germain', 'باريس سان جيرمان'], ['juventus', 'يوفنتوس'], ['ac milan', 'ميلان'], ['inter milan', 'إنتر ميلان']
];

var COMPETITION_NAMES = [
  ['saudi pro league', 'دوري روشن السعودي'], ['roshn', 'دوري روشن السعودي'],
  ['kings cup', 'كأس الملك'], ['king cup', 'كأس الملك'], ['custodian of the two holy mosques', 'كأس خادم الحرمين'],
  ['saudi super cup', 'كأس السوبر السعودي'], ['super cup', 'كأس السوبر'],
  ['afc champions league elite', 'دوري أبطال آسيا للنخبة'], ['afc champions league', 'دوري أبطال آسيا'],
  ['club world cup', 'كأس العالم للأندية'], ['premier league', 'الدوري الإنجليزي'], ['champions league', 'دوري أبطال أوروبا']
];

function normName(s) {
  s = String(s || '').toLowerCase();
  try { s = s.normalize('NFD').replace(/[̀-ͯ]/g, ''); } catch (e) { /* متصفح قديم بلا normalize */ }
  return s.replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').replace(/^ | $/g, '');
}

function lookupName(table, name) {
  var n = normName(name);
  for (var i = 0; i < table.length; i++) {
    if (n.indexOf(table[i][0]) !== -1) return table[i][1];
  }
  return null;
}

function arTeam(name) {
  var ar = lookupName(TEAM_NAMES, name);
  if (ar) return ar;
  return String(name || '').replace(/\b(FC|AFC|CF|SC)\b\.?/gi, '').replace(/\s+/g, ' ').replace(/^ | $/g, '');
}

function arCompetition(name) {
  return lookupName(COMPETITION_NAMES, name) || String(name || '');
}

// أسماء أبرز اللاعبين بالعربية لعرض مسجلي الأهداف (Goalscorers)
var PLAYER_NAMES = [
  // نجوم الهلال الحاليين (تشكيلة 2024 / 2025 / 2026)
  ['mitrovic', 'ميتروفيتش'], ['mitrović', 'ميتروفيتش'],
  ['al-dawsari', 'سالم الدوسري'], ['al dawsari', 'سالم الدوسري'],
  ['nasser al-dawsari', 'ناصر الدوسري'], ['nasser', 'ناصر الدوسري'],
  ['malcom', 'مالكوم'], ['neves', 'روبن نيفيز'],
  ['cancelo', 'كانسيلو'], ['joao cancelo', 'كانسيلو'],
  ['marcos leonardo', 'ماركوس ليوناردو'], ['leonardo', 'ماركوس ليوناردو'],
  ['moteb al-harbi', 'متعب الحربي'], ['al-harbi', 'متعب الحربي'],
  ['milinkovic-savic', 'سافيتش'], ['milinković-savić', 'سافيتش'], ['savic', 'سافيتش'],
  ['neymar', 'نيمار'], ['bounou', 'بونو'], ['bono', 'بونو'],
  ['koulibaly', 'كوليبالي'], ['lodi', 'رينان لودي'], ['renan lodi', 'لودي'],
  ['kanno', 'كنو'], ['mohamed kanno', 'كنو'],
  ['al-qahtani', 'محمد القحطاني'], ['al qahtani', 'القحطاني'],
  ['al-hamdan', 'الحمدان'], ['al-shehri', 'صالح الشهري'],
  ['al-shahrani', 'الشهراني'], ['al-bulayhi', 'البليهي'], ['tambakti', 'تمبكتي'],
  ['abdulhamid', 'سعود عبدالحميد'], ['al-malki', 'المالكي'], ['al-juwayr', 'الجوير'],
  ['al-yami', 'اليامي'], ['al-ghannam', 'خالد الغنام'],
  ['al-owais', 'العويس'], ['al-rubaie', 'الربيعي'],

  // نجوم الدوري السعودي والمنافسين
  ['benzema', 'بنزيمة'], ['ronaldo', 'رونالدو'], ['mane', 'ماني'],
  ['mahrez', 'محرز'], ['kante', 'كانتي'], ['firmino', 'فيرمينو'],
  ['talisca', 'تاليسكا'], ['brozovic', 'بروزوفيتش'], ['laporte', 'لابورت'],
  ['otavio', 'أوتافيو'], ['diaby', 'ديابي'], ['aouar', 'عوار'],
  ['kessie', 'كيسيه'], ['al-buraikan', 'البريكان'], ['toney', 'إيفان توني'],
  ['simakan', 'سيماكان'], ['mendy', 'ميندي'], ['bongonda', 'بونغوندا']
];

function arPlayer(name) {
  if (!name) return '';
  var ar = lookupName(PLAYER_NAMES, name);
  if (ar) return ar;
  // إن كان الاسم مكتوبًا بالعربية يظهر كاملًا كما هو
  var trimmed = String(name).trim();
  if (/[\u0600-\u06FF]/.test(trimmed)) return trimmed;
  // إن كان بالإنجليزية وغير موجود بالقاموس، نأخذ اللقب/الاسم الأخير مثل "Smith"
  var parts = trimmed.split(/\s+/);
  return parts.length > 1 ? parts[parts.length - 1] : trimmed;
}

// عرض مسجلي الأهداف في المباريات المباشرة والمنتهية
function renderMatchGoals(m, opp) {
  var goalsEl = $('match-goals');
  if (!goalsEl) return;
  if (!m || (m.status !== 'in' && m.status !== 'post')) {
    goalsEl.innerHTML = '';
    return;
  }
  var goals = m.goals || [];
  if (!goals.length) {
    goalsEl.innerHTML = '';
    return;
  }

  var myGoals = [];
  var oppGoals = [];
  for (var i = 0; i < goals.length; i++) {
    var g = goals[i];
    var name = arPlayer(g.player);
    var min = g.minute ? toLocaleDigits(String(g.minute).replace(/['\s]/g, '')) + "'" : '';
    var tag = '';
    if (g.isPenalty) tag = ' <span class="pen">(ض.ج)</span>';
    else if (g.isOwnGoal) tag = ' <span class="pen">(عكسي)</span>';
    var txt = esc(name) + (min ? ' ' + min : '') + tag;
    if (g.isMyTeam) myGoals.push(txt);
    else oppGoals.push(txt);
  }

  var html = '';
  var hasBoth = myGoals.length > 0 && oppGoals.length > 0;
  if (myGoals.length > 0) {
    html +=
      '<div class="g-line">' +
        '<i class="g-ball">⚽</i>' +
        (hasBoth ? '<span class="g-team">' + esc(TEAM_NAME) + ':</span>' : '') +
        '<span class="g-scorers">' + myGoals.join(' · ') + '</span>' +
      '</div>';
  }
  if (oppGoals.length > 0) {
    html +=
      '<div class="g-line">' +
        '<i class="g-ball">⚽</i>' +
        (hasBoth ? '<span class="g-team">' + esc(opp) + ':</span>' : '') +
        '<span class="g-scorers">' + oppGoals.join(' · ') + '</span>' +
      '</div>';
  }
  goalsEl.innerHTML = html;
}

var matchList = [];
var matchIdx = 0;
var matchSig = '';
var matchGen = 0; // يُلغي أي تبديل معلّق إذا وصلت قائمة جديدة في منتصف التلاشي

function matchWhen(m) {
  if (m.ts) {
    var diff = dayNumber(m.ts) - dayNumber(nowMs());
    var day;
    if (diff === 0) day = 'اليوم';
    else if (diff === 1) day = 'بكرة';
    else {
      var p = cityParts(m.ts);
      day = WEEKDAYS[p.wd] + ' ' + toLocaleDigits(p.d) + ' ' + MONTHS[p.mo];
    }
    return day + ' ' + fmtTime12(m.ts);
  }
  return ((m.date || '') + ' ' + toLocaleDigits(m.time || '')).replace(/^ | $/g, '');
}

// يصغّر خط العنوان تدريجيًا حتى يتسع في عرض العمود
function fitTitle(el) {
  var size = 36;
  while (el.scrollWidth > el.clientWidth + 1 && size > 20) {
    size -= 2;
    el.style.fontSize = 'calc(var(--px) * ' + size + ')';
  }
}

function buildDualMatchCard(m, clubName) {
  if (!m) {
    return '<div class="dual-card-row">' +
      '<div class="dual-card-top"><span class="dual-card-club">🔵 ' + esc(clubName) + '</span></div>' +
      '<div class="dual-card-fixture"><span class="team-name">لا توجد مباريات معلنة حالياً</span></div>' +
      '</div>';
  }
  var opp = arTeam(m.opponent);
  var comp = arCompetition(m.competition) || '';
  var isLive = m.status === 'in';
  var isFinished = m.status === 'post';
  var first = m.isHome ? clubName : opp;
  var second = m.isHome ? opp : clubName;
  var fixtureHtml = '';

  if (isLive) {
    var sFirst = toLocaleDigits(m.isHome ? (m.myScore || '0') : (m.oppScore || '0'));
    var sSecond = toLocaleDigits(m.isHome ? (m.oppScore || '0') : (m.myScore || '0'));
    fixtureHtml = '<span class="team-name">' + esc(first) + '</span> ' +
      '<span class="score-mini">' + sFirst + ' - ' + sSecond + '</span> ' +
      '<span class="team-name">' + esc(second) + '</span>';
  } else if (isFinished) {
    var sFirst = toLocaleDigits(m.isHome ? (m.myScore || '0') : (m.oppScore || '0'));
    var sSecond = toLocaleDigits(m.isHome ? (m.oppScore || '0') : (m.myScore || '0'));
    fixtureHtml = '<span class="team-name">' + esc(first) + '</span> ' +
      '<span class="score-mini">' + sFirst + ' - ' + sSecond + '</span> ' +
      '<span class="team-name">' + esc(second) + '</span>';
  } else {
    fixtureHtml = '<span class="team-name">' + esc(first) + '</span> ' +
      '<span class="vs-mini">VS</span> ' +
      '<span class="team-name">' + esc(second) + '</span>';
  }

  var subInfo = '';
  if (isLive) {
    subInfo = '🔴 مباشر الآن ' + (m.statusText ? '· ' + esc(m.statusText) : '');
  } else if (isFinished) {
    subInfo = 'انتهت · ' + esc(matchWhen(m));
  } else {
    subInfo = '📅 ' + esc(matchWhen(m));
  }
  if (m.venue) {
    subInfo += ' · 🏟️ ' + esc(m.venue.replace(/ \(.*\)/, ''));
  }

  return '<div class="dual-card-row">' +
    '<div class="dual-card-top">' +
      '<span class="dual-card-club">🔵 ' + esc(clubName) + '</span>' +
      (comp ? '<span class="dual-card-comp">' + esc(comp) + '</span>' : '') +
    '</div>' +
    '<div class="dual-card-fixture">' + fixtureHtml + '</div>' +
    '<div class="dual-card-sub">' + subInfo + '</div>' +
  '</div>';
}

function renderMatch() {
  var singleView = $('match-single-view');
  var dualView = $('match-dual-view');

  // إذا تم اختيار ناديين، نعرض مباراتي الناديين معاً في نفس الوقت جنباً إلى جنب
  if (TEAM2_NAME && TEAM2_NAME !== '' && TEAM2_NAME !== TEAM_NAME) {
    if (singleView) singleView.style.display = 'none';
    if (dualView) dualView.style.display = 'block';

    var m1 = null, m2 = null;
    for (var i = 0; i < matchList.length; i++) {
      var item = matchList[i];
      var cName = item.clubName || TEAM_NAME;
      if (!m1 && cName === TEAM_NAME) m1 = item;
      if (!m2 && cName === TEAM2_NAME) m2 = item;
    }
    var dualCards = $('dual-cards');
    if (dualCards) {
      dualCards.innerHTML = buildDualMatchCard(m1, TEAM_NAME) + buildDualMatchCard(m2, TEAM2_NAME);
    }
    return;
  }

  if (dualView) dualView.style.display = 'none';
  if (singleView) singleView.style.display = 'block';

  var titleEl = $('match-title');
  var labelEl = $('match-label');
  var compEl = $('match-comp-badge');
  var clubBadgeEl = $('match-club-badge');
  var nextLineEl = $('match-next-line');
  var goalsEl = $('match-goals');

  var m = matchList[matchIdx % matchList.length];
  if (!m) {
    if (labelEl) labelEl.textContent = 'مباراة ' + TEAM_NAME + ' القادمة';
    if (clubBadgeEl) clubBadgeEl.style.display = 'none';
    if (compEl) compEl.textContent = '';
    titleEl.className = 'value';
    titleEl.textContent = '—';
    setDotted('match-sub', ['لا توجد مباريات معلنة حالياً']);
    if (nextLineEl) nextLineEl.innerHTML = '';
    if (goalsEl) goalsEl.innerHTML = '';
    return;
  }

  var currentClub = m.clubName || TEAM_NAME;
  if (clubBadgeEl) {
    if (TEAM2_NAME && m.clubName) {
      clubBadgeEl.textContent = m.clubName;
      clubBadgeEl.style.display = 'inline-block';
    } else {
      clubBadgeEl.style.display = 'none';
    }
  }

  var opp = arTeam(m.opponent);
  var isLive = m.status === 'in';
  var isFinished = m.status === 'post';

  renderMatchGoals(m, opp);

  var compName = arCompetition(m.competition);
  if (compEl) compEl.textContent = compName || '';

  var matchdayEl = $('matchday-badge');
  var livePulseEl = $('match-live-pulse');
  var isToday = (m.ts && dayNumber(m.ts) === dayNumber(nowMs())) || (m.date && m.date.indexOf('اليوم') !== -1);
  if (matchdayEl) matchdayEl.style.display = (isToday && MATCHDAY_MODE_ENABLED) ? 'inline-block' : 'none';
  if (livePulseEl) livePulseEl.style.display = isLive ? 'inline-block' : 'none';

  var venueBadge = '';
  if (m.venue) {
    venueBadge = '<span class="venue-badge">🏟️ ' + esc(m.venue) + '</span>';
  } else if (m.isHome) {
    venueBadge = '<span class="venue-badge">🏟️ ملعب ' + esc(currentClub) + '</span>';
  }

  if (isLive) {
    if (labelEl) labelEl.innerHTML = '<span class="live-badge"><i class="live-dot"></i> مباشر الآن</span>';
    var first = m.isHome ? currentClub : opp;
    var second = m.isHome ? opp : currentClub;
    var sFirst = toLocaleDigits(m.isHome ? (m.myScore || '0') : (m.oppScore || '0'));
    var sSecond = toLocaleDigits(m.isHome ? (m.oppScore || '0') : (m.myScore || '0'));

    titleEl.className = 'value match-fixture-value';
    titleEl.style.fontSize = '';
    titleEl.innerHTML = '<span class="team-name">' + esc(first) + '</span>' +
      ' <span class="score-box">' + sFirst + ' - ' + sSecond + '</span> ' +
      '<span class="team-name">' + esc(second) + '</span>';
    fitTitle(titleEl);

    var parts = [];
    if (m.statusText) parts.push(esc(m.statusText));
    if (venueBadge) parts.push(venueBadge);
    else parts.push(m.isHome ? 'أرضي' : 'خارجي');
    setDotted('match-sub', parts);

    if (nextLineEl) {
      var nextM = matchList.length > 1 ? matchList[(matchIdx + 1) % matchList.length] : null;
      if (nextM) {
        var nClub = (TEAM2_NAME && nextM.clubName) ? (' [' + esc(nextM.clubName) + ']') : '';
        var nOpp = arTeam(nextM.opponent);
        var nWhen = matchWhen(nextM);
        nextLineEl.innerHTML = '<span class="next-pill">التالية' + nClub + '</span> ضد ' + esc(nOpp) + ' · ' + esc(nWhen);
      } else {
        nextLineEl.innerHTML = '';
      }
    }
  } else if (isFinished) {
    if (labelEl) labelEl.textContent = 'آخر نتيجة لـ ' + currentClub;
    var first = m.isHome ? currentClub : opp;
    var second = m.isHome ? opp : currentClub;
    var sFirst = toLocaleDigits(m.isHome ? (m.myScore || '0') : (m.oppScore || '0'));
    var sSecond = toLocaleDigits(m.isHome ? (m.oppScore || '0') : (m.myScore || '0'));

    titleEl.className = 'value match-fixture-value';
    titleEl.style.fontSize = '';
    titleEl.innerHTML = '<span class="team-name">' + esc(first) + '</span>' +
      ' <span class="score-box">' + sFirst + ' - ' + sSecond + '</span> ' +
      '<span class="team-name">' + esc(second) + '</span>';
    fitTitle(titleEl);

    var parts = ['انتهت'];
    if (m.date) parts.push(esc(matchWhen(m)));
    if (venueBadge) parts.push(venueBadge);
    setDotted('match-sub', parts);

    if (nextLineEl) {
      var nextM = matchList.length > 1 ? matchList[(matchIdx + 1) % matchList.length] : null;
      if (nextM) {
        var nClub = (TEAM2_NAME && nextM.clubName) ? (' [' + esc(nextM.clubName) + ']') : '';
        var nOpp = arTeam(nextM.opponent);
        var nWhen = matchWhen(nextM);
        nextLineEl.innerHTML = '<span class="next-pill">القادمة' + nClub + '</span> ضد ' + esc(nOpp) + ' · ' + esc(nWhen);
      } else {
        nextLineEl.innerHTML = '';
      }
    }
  } else {
    // مباراة مجدولة قادمة
    if (labelEl) labelEl.textContent = 'مباراة ' + currentClub + ' القادمة';
    var first = m.isHome ? currentClub : opp;
    var second = m.isHome ? opp : currentClub;

    titleEl.className = 'value match-fixture-value';
    titleEl.style.fontSize = '';
    titleEl.innerHTML = '<span class="team-name">' + esc(first) + '</span><span class="vs-badge">VS</span><span class="team-name">' + esc(second) + '</span>';
    fitTitle(titleEl);

    var parts = [esc(matchWhen(m))];
    if (venueBadge) parts.push(venueBadge);
    else parts.push(m.isHome ? ('أرضي (ملعب ' + esc(currentClub) + ')') : 'خارجي');
    setDotted('match-sub', parts);

    if (nextLineEl) {
      var nextM = matchList.length > 1 ? matchList[(matchIdx + 1) % matchList.length] : null;
      if (nextM) {
        var nClub = (TEAM2_NAME && nextM.clubName) ? (' [' + esc(nextM.clubName) + ']') : '';
        var nOpp = arTeam(nextM.opponent);
        var nWhen = matchWhen(nextM);
        var nVenue = nextM.venue ? ' (' + esc(nextM.venue.replace(/ \(.*\)/, '')) + ')' : '';
        nextLineEl.innerHTML = '<span class="next-pill">التالية' + nClub + '</span> ضد ' + esc(nOpp) + ' · ' + esc(nWhen) + nVenue;
      } else {
        nextLineEl.innerHTML = '';
      }
    }
  }
}

// ═══════════ شريط أخبار النادي ═══════════
var newsList = [];
var newsIdx = 0;
var newsTimer = null;
var SHOW_NEWS = true;

function updateNews(items) {
  newsList = items || [];
  newsIdx = 0;
  renderNews();
  if (newsTimer) clearInterval(newsTimer);
  if (newsList.length > 1) {
    newsTimer = setInterval(nextNews, NEWS_ROTATE_MS);
  }
}

function renderNews() {
  var bar = $('news-ticker-bar');
  if (!bar) return;
  if (!SHOW_NEWS || !newsList.length) {
    bar.style.display = 'none';
    return;
  }
  bar.style.display = 'flex';
  var item = newsList[newsIdx % newsList.length];
  var tagEl = $('news-tag');
  if (tagEl) {
    var club = item.clubName || TEAM_NAME || 'الهلال';
    tagEl.textContent = '𝕏 أخبار ' + club;
  }
  if ($('news-headline')) $('news-headline').textContent = item.headline || '';
  if ($('news-source')) {
    var src = item.source || '𝕏 @Alhilal_FC · تويتر';
    $('news-source').textContent = src;
  }
}

function nextNews() {
  if (!newsList.length) return;
  var bar = $('news-ticker-bar');
  if (bar) bar.style.opacity = '0';
  setTimeout(function() {
    newsIdx = (newsIdx + 1) % newsList.length;
    renderNews();
    if (bar) bar.style.opacity = '1';
  }, 400);
}

// ═══════════ تدوير المباريات بين الناديين ═══════════
var matchTimer = null;
function nextMatch() {
  if (!matchList || matchList.length <= 1) return;
  matchIdx = (matchIdx + 1) % matchList.length;
  renderMatch();
}
function startMatchRotation() {
  if (matchTimer) clearInterval(matchTimer);
  if (TEAM2_NAME && TEAM2_NAME !== '' && TEAM2_NAME !== TEAM_NAME) return;
  if (matchList && matchList.length > 1) {
    matchTimer = setInterval(nextMatch, MATCH_ROTATE_MS);
  }
}

function updateMatches(matches) {
  var list = matches || [];
  var live = null;
  var scheduled = [];
  var finished = [];

  for (var i = 0; i < list.length; i++) {
    var m = list[i];
    if (!m) continue;
    if (m.status === 'in') {
      live = m;
    } else if (m.status === 'post') {
      finished.push(m);
    } else {
      scheduled.push(m);
    }
  }

  // ترتيب المباريات القادمة تصاعدياً حسب التاريخ الأقرب دائماً
  scheduled.sort(function (a, b) {
    var tsA = typeof a.ts === 'number' ? a.ts : (a.kickoffMillis || 0);
    var tsB = typeof b.ts === 'number' ? b.ts : (b.kickoffMillis || 0);
    return tsA - tsB;
  });

  // ترتيب المباريات المنتهية تنازلياً (أحدث نتيجة أولاً)
  finished.sort(function (a, b) {
    var tsA = typeof a.ts === 'number' ? a.ts : (a.kickoffMillis || 0);
    var tsB = typeof b.ts === 'number' ? b.ts : (b.kickoffMillis || 0);
    return tsB - tsA;
  });

  var sorted = [];
  if (live) sorted.push(live);
  for (var s = 0; s < scheduled.length; s++) sorted.push(scheduled[s]);
  if (!live && scheduled.length === 0 && finished.length > 0) {
    sorted.push(finished[0]);
  }

  var sig = JSON.stringify(sorted);
  if (sig === matchSig) return;
  matchSig = sig;
  matchList = sorted;
  matchIdx = 0;
  renderMatch();
  startMatchRotation();
}

// ═══════════ خلفية الصور المتبدلة ═══════════
// Kotlin يقرأ أسماء الملفات من assets/backgrounds ويدفعها هنا مرة واحدة. بدون صور تبقى الخلفية navy.
var bgList = [];
var bgSig = '';
var bgIdx = -1;
var bgFront = -1; // رقم الطبقة الظاهرة حاليًا (0 أو 1)، -1 = لا شيء بعد
var bgTimer = null;
var bgLayers = [$('bg-a'), $('bg-b')];

function bgUrl(name) {
  if (/^(data:|blob:|https?:|file:)/.test(name)) return name; // للمعاينة فقط
  return 'backgrounds/' + encodeURIComponent(name);
}

function shuffle(arr) {
  for (var i = arr.length - 1; i > 0; i--) {
    var j = Math.floor(Math.random() * (i + 1));
    var t = arr[i]; arr[i] = arr[j]; arr[j] = t;
  }
  return arr;
}

function setBackgrounds(names) {
  var list = [];
  for (var i = 0; i < (names || []).length; i++) {
    if (typeof names[i] === 'string' && names[i]) list.push(names[i]);
  }
  var sig = list.join('|');
  if (sig === bgSig) return;
  bgSig = sig;

  if (bgTimer) { clearInterval(bgTimer); bgTimer = null; }
  bgList = shuffle(list); // ترتيب عشوائي في كل تشغيل
  bgIdx = -1;

  if (!bgList.length) {
    bgFront = -1;
    $('stage').className = 'stage';
    for (var k = 0; k < 2; k++) { bgLayers[k].className = 'bg-layer'; bgLayers[k].style.backgroundImage = 'none'; }
    return;
  }
  $('stage').className = 'stage has-photo';
  showNextBg();
  if (bgList.length > 1) bgTimer = setInterval(showNextBg, BG_INTERVAL_MS);
}

function showNextBg() {
  if (!bgList.length) return;
  bgIdx = (bgIdx + 1) % bgList.length;
  var name = bgList[bgIdx];
  var url = bgUrl(name);
  var applied = false;
  var img = new Image();

  function apply() {
    if (applied) return;
    applied = true;
    var back = bgFront === 0 ? 1 : 0;
    var layer = bgLayers[back];
    var old = bgFront >= 0 ? bgLayers[bgFront] : null;
    layer.style.backgroundImage = 'url("' + url + '")';
    layer.style.zIndex = 2;      // الجديدة فوق القديمة، فتتلاشى فوقها بدون وميض
    layer.className = 'bg-layer on';
    bgFront = back;
    if (old) {
      old.style.zIndex = 1;
      setTimeout(function () {
        if (bgLayers[bgFront] !== old) { // نحرر ذاكرة الصورة القديمة بعد اكتمال التلاشي
          old.className = 'bg-layer';
          old.style.backgroundImage = 'none';
        }
      }, BG_FADE_MS + 300);
    }
  }

  img.onload = function () {
    // decode() يفك ضغط الصورة قبل التلاشي حتى لا يتقطع الانتقال (إن كانت متاحة)
    if (img.decode) { img.decode().then(apply, apply); } else { apply(); }
  };
  img.onerror = function () {
    // ملف تالف: نحذفه من القائمة ونجرّب التالي
    var at = bgList.indexOf(name);
    if (at !== -1) bgList.splice(at, 1);
    if (bgIdx >= bgList.length) bgIdx = bgList.length - 1;
    if (!bgList.length && bgTimer) { clearInterval(bgTimer); bgTimer = null; }
    else if (bgList.length) showNextBg();
  };
  img.src = url;
}

// ═══════════ إعدادات المدينة والفريق (من ApiConfig.kt) ═══════════
// يدفعها Kotlin مرة واحدة عند تحميل الصفحة: { city, team, tz, offsetMs }.
// تغيّر نصوص «طقس ...» و«مباراة ... القادمة»، وتضبط المنطقة الزمنية للساعة والتواريخ والأوقات.
function setConfig(cfg) {
  if (!cfg) return;
  if (typeof cfg.city === 'string' && cfg.city) CITY_NAME = cfg.city;
  if (typeof cfg.team === 'string' && cfg.team) TEAM_NAME = cfg.team;
  if (typeof cfg.tz === 'string' && cfg.tz !== TZ_NAME) { TZ_NAME = cfg.tz; tzFmt = null; }
  if (typeof cfg.offsetMs === 'number' && isFinite(cfg.offsetMs)) TZ_FALLBACK_MS = cfg.offsetMs;
  if (cfg.iqamah && typeof cfg.iqamah === 'object') {
    for (var pk in cfg.iqamah) {
      if (typeof cfg.iqamah[pk] === 'number') IQAMAH_MINUTES[pk] = cfg.iqamah[pk];
    }
  }

  // التبديل التلقائي للودجات وسرعة تبديل الصور
  if ($('weather-box')) $('weather-box').style.display = cfg.showWeather === false ? 'none' : 'block';
  if ($('hadith-box')) $('hadith-box').style.display = cfg.showHadith === false ? 'none' : 'block';
  if ($('match-box')) $('match-box').style.display = cfg.showMatches === false ? 'none' : 'block';
  var pCol = document.querySelector('.col-prayers');
  if (pCol) pCol.style.display = cfg.showPrayers === false ? 'none' : 'block';
  var allCols = document.querySelectorAll('.col');
  if (allCols.length >= 2) allCols[1].style.display = cfg.showRamadan === false ? 'none' : 'block';
  if (typeof cfg.bgIntervalMs === 'number' && cfg.bgIntervalMs >= 10000) {
    BG_INTERVAL_MS = cfg.bgIntervalMs;
  }

  if (typeof cfg.prayerAlert === 'boolean') PRAYER_ALERT_ENABLED = cfg.prayerAlert;
  if (typeof cfg.matchdayMode === 'boolean') MATCHDAY_MODE_ENABLED = cfg.matchdayMode;
  if (typeof cfg.team2 === 'string') TEAM2_NAME = cfg.team2;
  if (typeof cfg.showNews === 'boolean') {
    SHOW_NEWS = cfg.showNews;
    renderNews();
  }
  if (typeof cfg.arabicNumerals === 'boolean') {
    USE_ARABIC_NUMERALS = cfg.arabicNumerals;
  }
  if (typeof cfg.matchRotateSec === 'number' && cfg.matchRotateSec >= 3) {
    MATCH_ROTATE_MS = cfg.matchRotateSec * 1000;
    startMatchRotation();
  }

  if (typeof cfg.fontFamily === 'string') {
    SELECTED_FONT = cfg.fontFamily;
    if (cfg.fontFamily === 'dubai') {
      document.documentElement.style.setProperty('--font-arabic', "'DubaiFont', 'Dubai', sans-serif");
    } else if (cfg.fontFamily === 'system') {
      document.documentElement.style.setProperty('--font-arabic', "system-ui, -apple-system, sans-serif");
    } else {
      document.documentElement.style.setProperty('--font-arabic', "'ThmanyahSans', 'Dubai', sans-serif");
    }
  }

  if (typeof cfg.fontScale === 'string') {
    FONT_SCALE = cfg.fontScale;
    if (cfg.fontScale === 'large') document.documentElement.style.fontSize = '120%';
    else if (cfg.fontScale === 'medium') document.documentElement.style.fontSize = '110%';
    else document.documentElement.style.fontSize = '100%';
  }

  if (typeof cfg.bgDim === 'string') {
    BG_DIM = cfg.bgDim;
    var dimEl = $('bg-dim-overlay');
    if (dimEl) {
      if (cfg.bgDim === 'light') dimEl.style.backgroundColor = 'rgba(0,0,0,0.2)';
      else if (cfg.bgDim === 'heavy') dimEl.style.backgroundColor = 'rgba(0,0,0,0.65)';
      else dimEl.style.backgroundColor = 'rgba(0,0,0,0.4)';
    }
  }

  if (typeof cfg.clockFormat === 'string') {
    USE_24H_CLOCK = (cfg.clockFormat === '24h');
  }

  if (typeof cfg.clockSeconds === 'boolean') {
    SHOW_SECONDS = cfg.clockSeconds;
  }

  if (typeof cfg.greeting === 'string') {
    GREETING_TEXT = cfg.greeting;
    var gEl = $('greeting-banner');
    if (gEl) {
      if (GREETING_TEXT) {
        gEl.textContent = GREETING_TEXT;
        gEl.style.display = 'block';
      } else {
        gEl.style.display = 'none';
      }
    }
  }

  if (typeof cfg.newsSpeedSec === 'number' && cfg.newsSpeedSec >= 4) {
    NEWS_ROTATE_MS = cfg.newsSpeedSec * 1000;
    if (newsTimer && newsList && newsList.length > 1) {
      clearInterval(newsTimer);
      newsTimer = setInterval(nextNews, NEWS_ROTATE_MS);
    }
  }

  // textContent وليس innerHTML: الأسماء نصوص عادية ولا تُفسَّر كـ HTML أبدًا
  if ($('weather-label')) $('weather-label').textContent = 'طقس ' + CITY_NAME;
  if ($('weather-box')) $('weather-box').setAttribute('aria-label', 'طقس ' + CITY_NAME);
  if ($('prayer-label')) $('prayer-label').textContent = 'الصلاة القادمة — ' + CITY_NAME;
  if ($('match-label')) $('match-label').textContent = 'مباراة ' + TEAM_NAME + ' القادمة';
  if ($('match-box')) $('match-box').setAttribute('aria-label', 'مباريات ' + TEAM_NAME + ' القادمة');

  // نعيد رسم كل ما يعتمد على التوقيت أو اسم الفريق (التاريخ، الساعة، الصلاة، رمضان، عنوان المباراة)
  lastDayNumber = null;
  tick();
}

// ═══════════ أحاديث نبوية صحيحة (الدرر السنية وبخاري ومسلم) ═══════════
var HADITHS = [
  { text: "«مَن صَلَّى لِلَّهِ اثْنَتَيْ عَشْرَةَ رَكْعَةً فِي يَوْمٍ وَلَيْلَةٍ، بُنِيَ له بِهِنَّ بَيْتٌ فِي الجَنَّةِ»", source: "صحيح مسلم · السنن الرواتب" },
  { text: "«مَن سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا، سَهَّلَ اللَّهُ له به طَرِيقًا إلى الجَنَّةِ»", source: "صحيح مسلم" },
  { text: "«كَلِمَتَانِ خَفِيفَتَانِ عَلَى اللِّسَانِ، ثَقِيلَتَانِ فِي المِيزَانِ، حَبِيبَتَانِ إِلَى الرَّحْمَنِ: سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ العَظِيمِ»", source: "متفق عليه" },
  { text: "«أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ»", source: "صحيح البخاري ومسلم" },
  { text: "«مَن قَرَأَ حَرْفًا مِنْ كِتَابِ اللَّهِ فَلَهُ بِهِ حَسَنَةٌ، وَالحَسَنَةُ بِعَشْرِ أَمْثَالِهَا»", source: "رواه الترمذي · صحيح" },
  { text: "«مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ»", source: "متفق عليه" },
  { text: "«مَن سَرَّهُ أَنْ يُبْسَطَ له في رِزْقِهِ، وَأَنْ يُنْسَأَ له في أَثَرِهِ، فَلْيَصِلْ رَحِمَهُ»", source: "صحيح البخاري" },
  { text: "«خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ»", source: "صحيح البخاري" },
  { text: "«اتَّقِ اللَّهَ حَيْثُمَا كُنْتَ، وَأَتْبِعِ السَّيِّئَةَ الحَسَنَةَ تَمْحُهَا، وَخَالِقِ النَّاسَ بِخُلُقٍ حَسَنٍ»", source: "رواه الترمذي · حسن صحيح" },
  { text: "«مَا نَقَصَتْ صَدَقَةٌ مِنْ مَالٍ، وَمَا زَادَ اللَّهُ عَبْدًا بِعَفْوٍ إِلَّا عِزًّا»", source: "صحيح مسلم" }
];
var hadithIdx = Math.floor(Math.random() * HADITHS.length);
function renderHadith() {
  var hEl = $('hadith-text');
  var sEl = $('hadith-source');
  if (!hEl || !sEl) return;
  var item = HADITHS[hadithIdx % HADITHS.length];
  hEl.textContent = item.text;
  sEl.textContent = item.source;
}
function nextHadith() {
  hadithIdx = (hadithIdx + 1) % HADITHS.length;
  renderHadith();
}

// ═══════════ التشغيل ═══════════
tick();
renderHadith();
setInterval(tick, 1000);
setInterval(nextHadith, 30000);
if (document.fonts && document.fonts.ready) document.fonts.ready.then(renderMatch);

