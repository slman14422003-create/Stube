/* Nova cosmetic filter (cosmetic.js): hides leftover ad containers once their network requests are blocked.
 * Conservative generic selectors only (Brave-style generic cosmetic rules), injected at document start.
 * Skipped on hosts where hiding could break the page. Any failure leaves the page untouched. */
(function () {
  'use strict';
  try {
    if (window.__novaCosmetic) return;
    Object.defineProperty(window, '__novaCosmetic', { value: 1 });
    var h = (location.hostname || '').toLowerCase();
    var T = __TRUSTED__;
    for (var ti = 0; ti < T.length; ti++) if (h === T[ti] || h.slice(-T[ti].length - 1) === '.' + T[ti]) return;
    try { if (localStorage.getItem('__novaFix') === '1') return; } catch (e) {}
    if (/(^|\.)(youtube\.com|youtu\.be|google\.[a-z.]+|gstatic\.com|cloudflare\.com|challenges\.cloudflare\.com|hcaptcha\.com|recaptcha\.net|github\.com|gitlab\.com)$/.test(h)) return;
    var css = [
      'ins.adsbygoogle', '.adsbygoogle-noablate', '.google-auto-placed',
      'iframe[id^="google_ads_iframe"]', 'div[id^="google_ads_iframe"]', 'div[id^="div-gpt-ad"]',
      'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication.com"]', 'iframe[src*="adservice.google"]',
      'amp-ad', 'amp-embed[type="taboola"]', 'amp-embed[type="outbrain"]',
      'div[id^="taboola-"]', '.OUTBRAIN[data-widget-id]', '.trc_rbox_container'
    ].join(',') + '{display:none!important}';
    var put = function () {
      var p = document.head || document.documentElement;
      if (!p) return false;
      var s = document.createElement('style');
      s.setAttribute('data-nova', 'cosmetic');
      s.textContent = css;
      p.appendChild(s);
      return true;
    };
    if (!put()) {
      var mo = new MutationObserver(function () { if (put()) mo.disconnect(); });
      mo.observe(document, { childList: true });
    }
  } catch (e) {}
})();
