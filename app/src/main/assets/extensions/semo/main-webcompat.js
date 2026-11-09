try{(function(){
/* Nova webcompat.js — طبقة دعم الفيديو والإطارات المضمّنة (تُحقن قبل سكربتات الصفحة في كل الإطارات).
 * 1) الإطارات المضمّنة (مشغّلات الفيديو): نضيف allowfullscreen وصلاحيات التشغيل/التشفير كي يعمل ملء الشاشة والتشغيل داخل iframe.
 * 2) فيديو انقطعت شبكته (MEDIA_ERR_NETWORK): إعادة محاولة مرتين مع حفظ موضع التشغيل بدل التوقف الدائم.
 * لا تعمل على يوتيوب/جوجل (لها WebView مخصّص وإدارتها الخاصة). كل جزء معزول بـ try/catch.
 */
(function () {
  try {
    if (window.__novaCompat) return;
    window.__novaCompat = 1;
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|youtu\.be|googlevideo\.com|google\.[a-z.]+|gstatic\.com)$/.test(location.hostname)) return;

    var ALLOW = ['autoplay', 'fullscreen', 'encrypted-media', 'picture-in-picture'];
    function fixFrame(f) {
      try {
        if (!f || f.tagName !== 'IFRAME' || f.__nvc) return;
        f.__nvc = 1;
        if (!f.hasAttribute('allowfullscreen')) f.setAttribute('allowfullscreen', '');
        var a = f.getAttribute('allow') || '';
        for (var i = 0; i < ALLOW.length; i++) if (a.indexOf(ALLOW[i]) < 0) a += (a ? '; ' : '') + ALLOW[i];
        f.setAttribute('allow', a);
      } catch (e) { }
    }
    try {
      var live = document.getElementsByTagName('iframe'), pend = 0;
      var idle = window.requestIdleCallback || function (f) { return setTimeout(f, 120); };
      var sweep = function () {
        pend = 0;
        try { for (var i = 0; i < live.length && i < 60; i++) fixFrame(live[i]); } catch (e) { }
      };
      var mo = new MutationObserver(function () { if (!pend) { pend = 1; idle(sweep, { timeout: 500 }); } });
      mo.observe(document, { childList: true, subtree: true });
      window.addEventListener('load', function () {
        sweep();
        setTimeout(function () { try { mo.disconnect(); } catch (e) { } }, 8000);
      });
    } catch (e) { }

    // إعادة محاولة الفيديو عند انقطاع الشبكة (الأحداث error لا تتصاعد، فنلتقطها في مرحلة الالتقاط)
    document.addEventListener('error', function (e) {
      try {
        var v = e.target;
        if (!v || v.tagName !== 'VIDEO' || !v.error || v.error.code !== 2) return;
        var src = v.currentSrc || v.src || '';
        if (!src || src.indexOf('blob:') === 0) return;      // MSE يدير نفسه
        v.__nvr = (v.__nvr || 0) + 1;
        if (v.__nvr > 2) return;
        setTimeout(function () {
          try {
            var t = v.currentTime || 0, was = !v.paused || v.autoplay;
            v.load();
            v.addEventListener('loadedmetadata', function f() {
              v.removeEventListener('loadedmetadata', f);
              try { if (t > 0) v.currentTime = t; if (was) { var p = v.play(); if (p && p.catch) p.catch(function () { }); } } catch (x) { }
            });
          } catch (x) { }
        }, 1200 * v.__nvr);
      } catch (x) { }
    }, true);
  } catch (e) { }
})();

}).call(window)}catch(e){}
