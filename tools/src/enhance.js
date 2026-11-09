(function () {
  try {
    if (window.__novaEnh) return;
    window.__novaEnh = 1;
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|youtu\.be|googlevideo\.com)$/.test(location.hostname)) return;
    try { if ('scrollRestoration' in history) history.scrollRestoration = 'auto'; } catch (e) { }

    var timer = 0;
    function active() {
      var e = document.activeElement;
      while (e && e.shadowRoot && e.shadowRoot.activeElement) e = e.shadowRoot.activeElement;
      return e;
    }
    function isField(e) {
      if (!e || e === document.body || e === document.documentElement) return false;
      var t = (e.tagName || '').toLowerCase();
      return t === 'input' || t === 'textarea' || t === 'select' || e.isContentEditable || e.getAttribute('role') === 'textbox';
    }
    function reveal() {
      try {
        var e = active();
        if (!isField(e)) return;
        var vv = window.visualViewport, h = vv ? vv.height : window.innerHeight, r = e.getBoundingClientRect();
        if (r.top < 0 || r.bottom > h * 0.9) e.scrollIntoView({ block: 'center', behavior: 'auto', inline: 'nearest' });
      } catch (x) { }
    }
    function later() { clearTimeout(timer); timer = setTimeout(reveal, 120); }
    window.addEventListener('resize', later, { passive: true });
    if (window.visualViewport) window.visualViewport.addEventListener('resize', later, { passive: true });
    document.addEventListener('focusin', function () { setTimeout(reveal, 320); }, true);

    var css = 'html,body{-webkit-tap-highlight-color:transparent}' +
      'input,textarea,select,button{touch-action:manipulation}' +
      'textarea,input[type=text],input[type=search],input[type=email],input[type=url],input[type=password],input[type=number]{scroll-margin-bottom:96px}' +
      'img,video,canvas,svg{-webkit-user-drag:none}';
    try {
      var sh = new CSSStyleSheet();
      sh.replaceSync(css);
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
    } catch (e) { }

    window.addEventListener('pageshow', function (ev) {
      try { if (ev.persisted) window.dispatchEvent(new Event('resize')); } catch (x) { }
    });
    function chunkReload() {
      try {
        var k = '__nvChunk:' + location.pathname;
        if (sessionStorage.getItem(k)) return;
        sessionStorage.setItem(k, '1');
        location.reload();
      } catch (x) { }
    }
    window.addEventListener('unhandledrejection', function (ev) {
      try {
        if (ev && ev.reason && /ChunkLoadError|Loading chunk|Failed to fetch dynamically imported module|error loading dynamically imported module/i.test(String(ev.reason.message || ev.reason))) chunkReload();
      } catch (x) { }
    });
    window.addEventListener('vite:preloadError', chunkReload);
  } catch (e) { }
})();
