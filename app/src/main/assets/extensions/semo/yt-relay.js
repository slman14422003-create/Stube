/* Semo YouTube relay (isolated content script) — جسر بين صفحة يوتيوب (yt-main.js في عالم الصفحة) وbackground.js.
 * صفحة → تطبيق: window.postMessage({__novaYt: "<json>"}) → runtime.sendMessage({yt}) → background → التطبيق.
 * تطبيق → صفحة: background → tabs.sendMessage({ytcfg | ytcall}) → window.postMessage → yt-main.js. */
(function () {
  "use strict";
  try {
    if (window.__novaYtRelay || window.top !== window) return;
    window.__novaYtRelay = 1;
    var api = typeof browser !== "undefined" ? browser : chrome;
    var cfg;                      // undefined = لم يصل بعد، null = لا إعدادات
    var ready = false;

    function toPage(msg) { try { window.postMessage(msg, location.origin); } catch (e) {} }
    function sendCfg() { if (cfg !== undefined) toPage({ __novaYtCfg: cfg }); }
    function toApp(s) {
      try {
        var p = api.runtime.sendMessage({ yt: s });
        if (p && p.catch) p.catch(function () {});
      } catch (e) {}
    }

    window.addEventListener("message", function (e) {
      if (e.source !== window || !e.data || typeof e.data !== "object") return;
      var d = e.data;
      if (typeof d.__novaYt === "string") {
        if (d.__novaYt.length <= 6000) toApp(d.__novaYt);
      } else if (d.__novaYtReady) {
        ready = true;
        sendCfg();
      }
    });

    api.runtime.onMessage.addListener(function (m) {
      if (!m || typeof m !== "object") return;
      if (m.ytcfg !== undefined && m.ytcfg !== null) { cfg = m.ytcfg; sendCfg(); }
      else if (m.ytcall && typeof m.fn === "string") toPage({ __novaYtCall: { fn: m.fn, args: m.args } });
    });

    try {
      var r = api.runtime.sendMessage({ ytcfg: true });
      if (r && r.then) r.then(function (c) { if (c) { cfg = c; sendCfg(); } }).catch(function () {});
    } catch (e) {}
    toApp(JSON.stringify({ t: "hello" }));
  } catch (e) {}
})();
