(function () {
  'use strict';
  if (window.__nvl) return;
  try { Object.defineProperty(window, '__nvl', { value: 1 }); } catch (e) {}
  if (window.top !== window) return;
  // الصفحات بلا lang تُعرض بخط/نموذج حروف خاطئ (أشهرها حروف الصينية/اليابانية/الكورية المشتركة). نستنتج اللغة من النص ونضبطها.
  var TRAD = '國學體說這們會從來個時對發經過後實點開問無為與關門車長東機書電話現學';
  var SIMP = '国学体说这们会从来个时对发经过后实点开问无为与关门车长东机书电话现学';
  var SCRIPTS = [
    [0x0900, 0x097F, 'hi'], [0x0E00, 0x0E7F, 'th'], [0x0590, 0x05FF, 'he'], [0x0370, 0x03FF, 'el'], [0x0980, 0x09FF, 'bn'],
    [0x0B80, 0x0BFF, 'ta'], [0x10A0, 0x10FF, 'ka'], [0x0530, 0x058F, 'hy'], [0x1780, 0x17FF, 'km'], [0x0E80, 0x0EFF, 'lo'],
    [0x1000, 0x109F, 'my'], [0x0C00, 0x0C7F, 'te'], [0x0C80, 0x0CFF, 'kn'], [0x0D00, 0x0D7F, 'ml'], [0x0A80, 0x0AFF, 'gu'],
    [0x0A00, 0x0A7F, 'pa'], [0x0D80, 0x0DFF, 'si'], [0x1200, 0x137F, 'am'], [0x0F00, 0x0FFF, 'bo'], [0x0780, 0x07BF, 'dv']
  ];
  function detect(t) {
    var c = { ja: 0, ko: 0, han: 0, ar: 0, cy: 0 }, sc = {}, n = 0, i, k, j;
    for (i = 0; i < t.length; i++) {
      k = t.charCodeAt(i);
      if (k < 0x41 || (k > 0x5A && k < 0x61) || (k > 0x7A && k < 0xC0)) continue;
      n++;
      if (k >= 0x3040 && k <= 0x30FF) c.ja++;
      else if ((k >= 0xAC00 && k <= 0xD7AF) || (k >= 0x1100 && k <= 0x11FF)) c.ko++;
      else if ((k >= 0x4E00 && k <= 0x9FFF) || (k >= 0x3400 && k <= 0x4DBF)) c.han++;
      else if ((k >= 0x0600 && k <= 0x06FF) || (k >= 0x0750 && k <= 0x077F)) c.ar++;
      else if (k >= 0x0400 && k <= 0x04FF) c.cy++;
      else for (j = 0; j < SCRIPTS.length; j++) if (k >= SCRIPTS[j][0] && k <= SCRIPTS[j][1]) { sc[SCRIPTS[j][2]] = (sc[SCRIPTS[j][2]] || 0) + 1; break; }
    }
    if (n < 15) return '';
    if (c.ja >= 3 && (c.ja + c.han) / n > 0.25) return 'ja';
    if (c.ko / n > 0.25) return 'ko';
    if (c.han / n > 0.25) {
      var tr = 0, sm = 0;
      for (j = 0; j < t.length; j++) { if (TRAD.indexOf(t.charAt(j)) >= 0) tr++; if (SIMP.indexOf(t.charAt(j)) >= 0) sm++; }
      return tr > sm ? 'zh-Hant' : 'zh-Hans';
    }
    if (c.ar / n > 0.4) {
      if (/[\u067E\u0686\u0698\u06AF]/.test(t)) return 'fa';
      if (/[\u0679\u0688\u0691\u06BA\u06D2]/.test(t)) return 'ur';
      if (/[\u06C6\u06CB\u06D5]/.test(t)) return 'ug';
      return 'ar';
    }
    if (c.cy / n > 0.4) {
      if (/[\u0456\u0457\u0454\u0491]/.test(t)) return 'uk';
      if (/\u045E/.test(t)) return 'be';
      if (/[\u04D9\u04E9\u04AF\u049B\u04A3\u04BB]/.test(t)) return 'kk';
      if (/[\u0452\u0459\u045A\u045B\u045F]/.test(t)) return 'sr';
      if ((t.match(/\u044A/g) || []).length > 3 && !/[\u044B\u044D]/.test(t)) return 'bg';
      return 'ru';
    }
    for (var key in sc) if (sc[key] / n > 0.4) return key;
    return '';
  }
  function sample(limit) {
    var out = '', n, c = 0;
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (t) {
        var p = t.parentNode, nm = p && p.nodeName;
        return (!p || nm === 'SCRIPT' || nm === 'STYLE' || nm === 'NOSCRIPT' || nm === 'TEMPLATE') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });
    while (out.length < limit && c++ < 400 && (n = w.nextNode())) {
      var s = n.nodeValue && n.nodeValue.trim();
      if (s) out += s + ' ';
    }
    return out.slice(0, limit);
  }
  function metaLang() {
    try {
      var m = document.querySelector('meta[http-equiv="content-language" i],meta[name="language" i],meta[property="og:locale"]');
      var v = m && m.getAttribute('content');
      if (v && /^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$/.test(v.trim())) return v.trim().replace('_', '-');
    } catch (e) {}
    return '';
  }
  var tries = 0;
  function run() {
    try {
      var h = document.documentElement;
      if (!h || h.getAttribute('lang') || !document.body) { if (++tries < 3 && h && !h.getAttribute('lang')) setTimeout(run, 1500); return; }
      var code = detect(sample(1500)) || metaLang();
      if (code) h.setAttribute('lang', code);
      else if (++tries < 3) setTimeout(run, 1500);
    } catch (e) {}
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run, { once: true }); else run();
})();
