Object.defineProperty(window, '__novaTr', {
  value: function () {
    try {
      var T = __TRUSTED__, h = String(location.hostname).toLowerCase();
      for (var i = 0; i < T.length; i++) if (h === T[i] || h.slice(-T[i].length - 1) === '.' + T[i]) return true;
      return localStorage.getItem('__novaFix') === '1';
    } catch (e) { return false; }
  },
  configurable: true
});
/* Nova smooth.js — سكربت السلاسة الموحّد (كان render.js + boost.js + تحميل كسول مكرر في Perf.kt). يُحقن مرة واحدة قبل سكربتات الصفحة.
 * العلامات: __FIT__ تحسين العرض • __LAZY__ تحميل كسول للصور • __BOOST__ جلب مسبق + إيقاف فيديو صامت • __POP__ لافتات الكوكيز.
 * سياسة الصور واحدة فقط: فك ترميز غير متزامن، وتأجيل ما يبعد أكثر من شاشتين تحت الصفحة (كان الكود القديم يؤجّل كل الصور حتى العلوية).
 * لا يعمل على يوتيوب/جوجل: لها WebView مخصّص (YtWeb.kt).
 */
(function () {
  try {
    if (window.__novaRender || !(__FIT__ || __LAZY__)) return;
    window.__novaRender = 1;
    if (!/Android/i.test(navigator.userAgent)) return;

    // يوتيوب/جوجل: صفحاتها تدير تخطيطها بنفسها (المشغّل والواجهة الأصلية)، فلا نضيف شيئاً
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    var css =
      'html{-webkit-text-size-adjust:100%;text-size-adjust:100%;-webkit-tap-highlight-color:transparent}' + // منع تضخيم الخط + إزالة وميض اللمس
      // لا optimizeLegibility ولا text-wrap:pretty: كلاهما يزيد كلفة تخطيط النص في كل الصفحة (تقطيع على الصفحات الطويلة والأجهزة المتوسطة)
      'a,button,summary,label,input,select,[role=button]{touch-action:manipulation}' + // إلغاء تأخير اللمس المزدوج
      'img{image-orientation:from-image}' +
      ':focus-visible{outline:2px solid #4c8dff;outline-offset:2px;border-radius:4px}' +
      '::selection{background:rgba(76,141,255,.35)}' +
      'html{scrollbar-width:thin;overscroll-behavior-x:none}' +                       // لا سحب جانبي عرضي مزعج
      '@media (prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}';

    // ورقة أنماط مبنية برمجياً: لا تتأثر بسياسات CSP التي تمنع <style> المضمّن، وتعمل قبل وجود <head>
    if (__FIT__ && !window.__novaTr()) try {
      var sh = new CSSStyleSheet();
      sh.replaceSync(css);
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
    } catch (e) { /* متصفح قديم: نتجاهل */ }

    // حارس التجاوز الأفقي: يعمل فقط للصفحات المتجاوبة (viewport=device-width) التي كسر عنصر واحد عرضها
    function guard() {
      try {
        var m = document.querySelector('meta[name=viewport]');
        if (!m || !/device-width/i.test(m.content)) return;
        var de = document.documentElement;
        if (de.scrollWidth > window.innerWidth * 1.02) de.style.setProperty('overflow-x', 'hidden');
      } catch (e) { }
    }
    if (__FIT__ && !window.__novaTr()) window.addEventListener('load', function () { setTimeout(guard, 400); });

    // الصور: فك الترميز بعيداً عن خيط الواجهة، وتأجيل ما هو بعيد أسفل الصفحة (مرة واحدة وبحدّ أعلى)
    function imgs() {
      try {
        var l = document.images, n = Math.min(l.length, 300), vh = window.innerHeight * 2;
        var todo = [], defer = [], i;
        for (i = 0; i < n; i++) {
          if (l[i].__nv) continue;
          l[i].__nv = 1;
          todo.push(l[i]);
        }
        // reads first, writes after: no forced layout between them
        if (__LAZY__) {
          for (i = 0; i < todo.length; i++) {
            var m = todo[i];
            if (!m.hasAttribute('loading') && !m.complete && m.getBoundingClientRect().top > vh) defer.push(m);
          }
        }
        for (i = 0; i < todo.length; i++) if (!todo[i].hasAttribute('decoding')) todo[i].decoding = 'async';
        for (i = 0; i < defer.length; i++) defer[i].loading = 'lazy';
      } catch (e) { }
    }
    var ric = window.requestIdleCallback || function (f) { return setTimeout(f, 60); };
    document.addEventListener('DOMContentLoaded', function () { setTimeout(function () { ric(imgs, { timeout: 800 }); }, 50); });
    window.addEventListener('load', function () { setTimeout(function () { ric(imgs, { timeout: 800 }); }, 300); });
  } catch (e) { }
})();
// 1) جلب مسبق لروابط نفس الموقع عند لمس الرابط أكثر من 90ms (يُلغى مع أي سحب/تمرير).
// 2) إيقاف الفيديوهات الصامتة (خلفيات متحركة) خارج الشاشة لتوفير المعالج والبطارية والحرارة.
(function () {
  try {
    if (window.__novaBoost) return;
    window.__novaBoost = 1;
    var BOOST = __BOOST__;
    if (!/^https?:$/.test(location.protocol)) return;
    // يوتيوب/جوجل: تدير تحميلها ومشغّلها بنفسها
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    var cn = navigator.connection || {};
    var slow = !!cn.saveData || /(^|-)2g$/.test(cn.effectiveType || '');

    // ---------- 1) الجلب المسبق ----------
    if (BOOST) {
    var seen = {}, count = 0, timer = 0, target = null;
    var SKIP_FILE = /\.(zip|apk|pdf|exe|mp4|mp3|mkv|rar|7z|iso|dmg|torrent)$/i;
    var SKIP_PATH = /(logout|signout|sign-out|log-out|delete|remove|unsubscribe|checkout|cart|payment|\/pay\b|confirm|activate|verify)/i;

    function prefetch(a) {
      try {
        if (slow || count >= 12 || !a || !a.href) return;
        var u = new URL(a.href, location.href);
        if (u.origin !== location.origin) return;                       // نفس الموقع فقط: لا تسريب لمواقع أخرى
        if (u.href.split('#')[0] === location.href.split('#')[0]) return;
        if (a.target && a.target !== '_self') return;
        if (a.hasAttribute('download') || SKIP_FILE.test(u.pathname) || SKIP_PATH.test(u.pathname + u.search)) return;
        if (seen[u.href]) return;
        seen[u.href] = 1; count++;
        var l = document.createElement('link');
        l.rel = 'prefetch'; l.href = u.href; l.as = 'document';
        (document.head || document.documentElement).appendChild(l);
      } catch (e) { }
    }

    document.addEventListener('touchstart', function (e) {
      var t = e.target;
      var a = t && t.closest ? t.closest('a[href]') : null;
      if (!a) return;
      target = a;
      clearTimeout(timer);
      timer = setTimeout(function () { prefetch(target); }, 90);
    }, { passive: true, capture: true });
    ['touchmove', 'touchend', 'touchcancel'].forEach(function (n) {
      document.addEventListener(n, function () { clearTimeout(timer); }, { passive: true, capture: true });
    });

    }
    // ---------- 2) إيقاف الفيديو الصامت خارج الشاشة ----------
    if (BOOST && 'IntersectionObserver' in window) {
      var io = new IntersectionObserver(function (es) {
        es.forEach(function (en) {
          var v = en.target;
          try {
            if (!en.isIntersecting) {
              if (!v.paused && v.muted && !v.controls && !v.ended) { v.__nbp = 1; v.pause(); }
            } else if (v.__nbp) {
              v.__nbp = 0;
              var p = v.play();
              if (p && p.catch) p.catch(function () { });
            }
          } catch (e) { }
        });
      }, { rootMargin: '150px' });

      var watch = function () {
        try {
          var l = document.getElementsByTagName('video');
          for (var i = 0; i < l.length && i < 40; i++) {
            if (!l[i].__nbw) { l[i].__nbw = 1; io.observe(l[i]); }
          }
        } catch (e) { }
      };
      document.addEventListener('DOMContentLoaded', watch);
      window.addEventListener('load', function () { setTimeout(watch, 400); setTimeout(watch, 3000); });
    }
    // ---------- 3) تنظيف لافتات الموافقة على الكوكيز وقفل التمرير (يُطفأ من الإعدادات) ----------
    if (__POP__ && !window.__novaTr()) {
      var CONSENT = '#onetrust-consent-sdk,#onetrust-banner-sdk,#CybotCookiebotDialog,#CybotCookiebotDialogBodyUnderlay,.cc-window,.cc-banner,' +
        '#cookie-law-info-bar,#cookieconsent,.cookie-consent,#cookie-consent,.cookie-banner,#cookie-banner,#gdpr-cookie-notice,.qc-cmp2-container,' +
        '#didomi-host,.fc-consent-root,#usercentrics-root,.osano-cm-window,#cmpbox,#cmpbox2,#iubenda-cs-banner,.klaro,.truste_overlay,' +
        '[id^="sp_message_container"],[id^="CookieBoxSaveButton"]';
      try {
        var psh = new CSSStyleSheet();
        psh.replaceSync(CONSENT + '{display:none!important}');
        document.adoptedStyleSheets = document.adoptedStyleSheets.concat([psh]);
      } catch (e) { }
      var unlockTimes = 0;
      function unlock() {
        try {
          if (!document.querySelector(CONSENT)) return;          // لا لافتة معروفة: لا نلمس التمرير
          [document.documentElement, document.body].forEach(function (el) {
            if (el && getComputedStyle(el).overflowY === 'hidden') el.style.setProperty('overflow', 'auto', 'important');
          });
        } catch (e) { }
      }
      document.addEventListener('DOMContentLoaded', function () { unlock(); setTimeout(unlock, 800); });
      window.addEventListener('load', function () { setTimeout(unlock, 400); setTimeout(unlock, 2500); });
    }
  } catch (e) { }
})();

// ───────── وضع الصفحات الثقيلة: صفحات الأكواد الضخمة والـ DOM الكبير ─────────
// عند اكتشاف صفحة ثقيلة: تصيير نص أسرع (بلا ligatures/kerning)، وتأجيل تخطيط كتل الأكواد البعيدة عن الشاشة (content-visibility).
// الصفحة نفسها لا تتغير في الشكل، وتبقى كل الكتل قابلة للبحث والنسخ.
(function () {
  try {
    if (window.__novaHeavy || window.top !== window) return;
    var host = String(location.hostname);
    if (/(^|\.)(youtube\.com|youtu\.be|google\.[a-z.]+)$/.test(host) || window.__novaTr()) return;
    window.__novaHeavy = 1;
    var applied = false;
    function check() {
      if (applied || !document.body) return;
      var pre = document.getElementsByTagName('pre');
      var heavy = document.getElementsByTagName('*').length > 5000 || pre.length > 20;
      for (var i = 0; !heavy && i < pre.length && i < 20; i++) { var t = pre[i].textContent; if (t && t.length > 60000) heavy = true; }
      if (!heavy) return;
      applied = true;
      var st = document.createElement('style');
      st.textContent = 'pre,code,kbd,samp,.highlight,.blob-code,.blob-code-inner,.CodeMirror,.cm-content,.monaco-editor{text-rendering:optimizeSpeed!important;font-variant-ligatures:none!important;font-kerning:none!important}html{scroll-behavior:auto!important}';
      (document.head || document.documentElement).appendChild(st);
      if (pre.length > 20) {
        for (var j = 3; j < pre.length; j++) {
          var p = pre[j];
          if (p.closest && p.closest('table')) continue;
          p.style.setProperty('content-visibility', 'auto');
          p.style.setProperty('contain-intrinsic-size', 'auto 300px');
        }
      }
    }
    var ric2 = window.requestIdleCallback || function (f) { return setTimeout(f, 200); };
    function later(ms) { setTimeout(function () { if (!applied) ric2(check, { timeout: 1500 }); }, ms); }
    document.addEventListener('DOMContentLoaded', function () { later(1200); });
    window.addEventListener('load', function () { later(1500); later(5000); });
  } catch (e) { }
})();
