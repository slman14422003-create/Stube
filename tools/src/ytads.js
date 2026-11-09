/* Nova YouTube ad blocker. Client side only: independent of network, DNS, region or VPN. */
try {
(function () {
  'use strict';
  if (window.__novaYtAds || window.top !== window || location.hostname === 'music.youtube.com') return;
  window.__novaYtAds = 1;
  var ON = __ADS__;
  if (!ON) return;

  var KEYS = ['adPlacements', 'playerAds', 'adSlots'];
  var AD_ITEM = /^(adSlotRenderer|promotedSparklesWebRenderer|promotedVideoRenderer|compactPromotedVideoRenderer|displayAdRenderer|inFeedAdLayoutRenderer|bannerPromoRenderer|statementBannerRenderer|brandVideoSingletonRenderer|brandVideoShelfRenderer|companionAdRenderer|adBreakServiceRenderer)$/;
  var AD_RE = /adPlacements|playerAds|adSlots|adSlotRenderer|promotedSparklesWebRenderer|promotedVideoRenderer|displayAdRenderer|inFeedAdLayoutRenderer|brandVideoSingletonRenderer/;

  function isAd(el, d) {
    if (!el || typeof el !== 'object') return false;
    for (var k in el) {
      if (AD_ITEM.test(k)) return true;
      if (d < 2 && el[k] && typeof el[k] === 'object' && isAd(el[k], d + 1)) return true;
    }
    return false;
  }

  function walk(o, depth, st) {
    if (!o || typeof o !== 'object' || depth > 16 || st.n > 40000) return;
    st.n++;
    if (Array.isArray(o)) {
      for (var i = o.length - 1; i >= 0; i--) {
        if (isAd(o[i], 0)) o.splice(i, 1); else walk(o[i], depth + 1, st);
      }
    } else {
      for (var k in o) {
        if (KEYS.indexOf(k) >= 0) { delete o[k]; continue; }
        var v = o[k];
        if (v && typeof v === 'object') walk(v, depth + 1, st);
      }
    }
  }

  function clean(o) { try { walk(o, 0, { n: 0 }); } catch (e) {} return o; }

  var nativeParse = JSON.parse;
  JSON.parse = function (t, r) {
    var o = nativeParse.call(this, t, r);
    try { if (typeof t === 'string' && t.length > 400 && AD_RE.test(t)) clean(o); } catch (e) {}
    return o;
  };

  var nativeFetch = window.fetch;
  if (typeof nativeFetch === 'function') {
    window.fetch = function (input) {
      var p = nativeFetch.apply(this, arguments);
      var url = '';
      try { url = typeof input === 'string' ? input : (input && input.url) || ''; } catch (e) {}
      if (!/\/youtubei\/v1\/(player|next|browse|search|reel\/)/.test(url)) return p;
      return p.then(function (res) {
        try {
          if (!res || !res.ok) return res;
          return res.clone().text().then(function (txt) {
            if (!AD_RE.test(txt)) return res;
            var o = clean(nativeParse.call(JSON, txt));
            return new Response(JSON.stringify(o), { status: res.status, statusText: res.statusText, headers: res.headers });
          }).catch(function () { return res; });
        } catch (e) { return res; }
      });
    };
  }

  ['ytInitialPlayerResponse', 'ytInitialData'].forEach(function (n) {
    var val;
    try {
      Object.defineProperty(window, n, {
        configurable: true,
        get: function () { return val; },
        set: function (x) { val = clean(x); }
      });
    } catch (e) {}
  });

  var CSS =
    'ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,ytm-display-ad-renderer,' +
    'ytm-ad-slot-renderer,ytm-in-feed-ad-layout-renderer,ytm-statement-banner-renderer,ytm-brand-video-singleton-renderer,' +
    'ytm-brand-video-shelf-renderer,ytm-banner-promo-renderer,ytd-ad-slot-renderer,ytd-display-ad-renderer,' +
    'ytd-promoted-sparkles-web-renderer,ytd-in-feed-ad-layout-renderer,.ytp-ad-overlay-container,.ytp-ad-image-overlay,' +
    '.ytp-ad-text-overlay,.ytp-paid-content-overlay,#player-ads,#masthead-ad,' +
    'ytm-rich-item-renderer:has(ytm-ad-slot-renderer),ytm-rich-item-renderer:has(ytm-in-feed-ad-layout-renderer),' +
    'ytm-item-section-renderer:has(ytm-ad-slot-renderer){display:none!important}';

  function inject() {
    if (document.getElementById('nova-ytads')) return true;
    var h = document.head || document.documentElement;
    if (!h) return false;
    var s = document.createElement('style');
    s.id = 'nova-ytads';
    s.textContent = CSS;
    h.appendChild(s);
    return true;
  }
  if (!inject()) document.addEventListener('DOMContentLoaded', inject);

  var held = null;
  function tick() {
    try {
      inject();
      var p = document.querySelector('#movie_player, .html5-video-player');
      var v = p && p.querySelector('video');
      var ad = !!(p && p.classList && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')));
      if (ad && v) {
        if (!held) held = { muted: v.muted, rate: v.playbackRate };
        v.muted = true;
        try { v.playbackRate = 16; } catch (e) {}
        if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.2) {
          try { v.currentTime = v.duration - 0.1; } catch (e) {}
        }
        var b = document.querySelector('.ytp-ad-skip-button-modern,.ytp-ad-skip-button,.ytp-skip-ad-button,.ytp-ad-skip-button-container button');
        if (b) b.click();
        var c = document.querySelector('.ytp-ad-overlay-close-button');
        if (c) c.click();
      } else if (held && v) {
        v.muted = held.muted;
        try { v.playbackRate = held.rate || 1; } catch (e) {}
        held = null;
      }
    } catch (e) {}
  }
  setInterval(tick, 250);
})();
} catch (e) {}
