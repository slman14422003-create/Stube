try{(function(){
/* Nova cosmetic filter (cosmetic.js): hides leftover ad containers once their network requests are blocked.
 * Conservative generic selectors only (Brave-style generic cosmetic rules), injected at document start.
 * Skipped on hosts where hiding could break the page. Any failure leaves the page untouched. */
(function () {
  'use strict';
  try {
    if (window.__novaCosmetic) return;
    Object.defineProperty(window, '__novaCosmetic', { value: 1 });
    var h = (location.hostname || '').toLowerCase();
    var T = ["firebase.google.com", "firebaseapp.com", "web.app", "firebaseio.com", "cloud.google.com", "console.cloud.google.com", "cloudflare.com", "cloudflareaccess.com", "pages.dev", "workers.dev", "cloudflarestorage.com", "github.com", "github.dev", "gitlab.com", "bitbucket.org", "vercel.com", "vercel.app", "netlify.com", "netlify.app", "supabase.com", "supabase.co", "render.com", "onrender.com", "heroku.com", "herokuapp.com", "railway.app", "fly.io", "digitalocean.com", "amazon.com", "aws.amazon.com", "amazonaws.com", "azure.com", "azurewebsites.net", "microsoft.com", "microsoftonline.com", "live.com", "office.com", "atlassian.net", "atlassian.com", "slack.com", "notion.so", "figma.com", "stripe.com", "paypal.com", "auth0.com", "okta.com", "apple.com", "facebook.com", "linkedin.com", "x.com", "twitter.com", "discord.com", "openai.com", "chatgpt.com", "claude.ai", "anthropic.com", "huggingface.co", "expo.dev", "mongodb.com", "neon.tech", "sentry.io", "datadoghq.com", "codepen.io", "jsfiddle.net", "stackblitz.com", "codesandbox.io", "replit.com", "glitch.com", "glitch.me", "google.com", "googleusercontent.com", "gstatic.com"];
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

}).call(window)}catch(e){}
