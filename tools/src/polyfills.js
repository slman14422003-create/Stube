(function () {
  'use strict';
  if (window.__nvp) return;
  try { Object.defineProperty(window, '__nvp', { value: 1 }); } catch (e) {}
  if (/(^|\.)(challenges\.cloudflare\.com|hcaptcha\.com|recaptcha\.net|captcha-delivery\.com|datadome\.co|arkoselabs\.com|geetest\.com)$/.test(String(location.hostname))) return;
  function def(o, n, f) {
    try { if (o && typeof o[n] === 'undefined') Object.defineProperty(o, n, { value: f, writable: true, configurable: true }); } catch (e) {}
  }
  var A = Array.prototype, TA = (typeof Uint8Array !== 'undefined') ? Object.getPrototypeOf(Uint8Array.prototype) : null;
  function toInt(x) { x = Math.trunc(Number(x)); return x !== x ? 0 : x; }

  // ---------- Array / TypedArray ----------
  def(A, 'at', function (i) { i = toInt(i); if (i < 0) i += this.length; return this[i]; });
  def(A, 'findLast', function (f, t) { for (var i = this.length - 1; i >= 0; i--) if (f.call(t, this[i], i, this)) return this[i]; });
  def(A, 'findLastIndex', function (f, t) { for (var i = this.length - 1; i >= 0; i--) if (f.call(t, this[i], i, this)) return i; return -1; });
  def(A, 'toReversed', function () { return Array.prototype.slice.call(this).reverse(); });
  def(A, 'toSorted', function (c) { return Array.prototype.slice.call(this).sort(c); });
  def(A, 'toSpliced', function () { var a = Array.prototype.slice.call(this); a.splice.apply(a, arguments); return a; });
  def(A, 'with', function (i, v) { var a = Array.prototype.slice.call(this), n = a.length; i = toInt(i); if (i < 0) i += n; if (i < 0 || i >= n) throw new RangeError('Invalid index'); a[i] = v; return a; });
  if (TA) { def(TA, 'at', A.at); def(TA, 'findLast', A.findLast); def(TA, 'findLastIndex', A.findLastIndex); }
  def(Array, 'fromAsync', function (it, f) {
    return (async function () { var r = [], i = 0; for await (var v of it) r.push(f ? await f(v, i++) : v); return r; })();
  });

  // ---------- Object / Map ----------
  def(Object, 'hasOwn', function (o, k) { return Object.prototype.hasOwnProperty.call(Object(o), k); });
  def(Object, 'groupBy', function (it, f) { var r = Object.create(null), i = 0; for (var v of it) { var k = f(v, i++); (r[k] || (r[k] = [])).push(v); } return r; });
  def(Map, 'groupBy', function (it, f) { var r = new Map(), i = 0; for (var v of it) { var k = f(v, i++); if (!r.has(k)) r.set(k, []); r.get(k).push(v); } return r; });

  // ---------- String ----------
  def(String.prototype, 'at', function (i) { i = toInt(i); if (i < 0) i += this.length; return i < 0 || i >= this.length ? undefined : String(this).charAt(i); });
  def(String.prototype, 'replaceAll', function (a, b) {
    if (a instanceof RegExp) { if (!a.global) throw new TypeError('replaceAll must be called with a global RegExp'); return String(this).replace(a, b); }
    var s = String(this), pat = String(a);
    if (typeof b === 'function') return s.split(pat).reduce(function (acc, part, idx, arr) { return acc + part + (idx < arr.length - 1 ? b(pat) : ''); }, '');
    return s.split(pat).join(String(b));
  });
  def(String.prototype, 'isWellFormed', function () { return !/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF]/.test(String(this)); });
  def(String.prototype, 'toWellFormed', function () { return String(this).replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF]/g, function (m) { return m.length === 2 ? m.charAt(0) + '\uFFFD' : '\uFFFD'; }); });

  // ---------- Promise ----------
  def(Promise, 'withResolvers', function () { var res, rej, p = new this(function (a, b) { res = a; rej = b; }); return { promise: p, resolve: res, reject: rej }; });
  def(Promise, 'allSettled', function (it) {
    return Promise.all(Array.prototype.map.call(Array.from(it), function (p) {
      return Promise.resolve(p).then(function (v) { return { status: 'fulfilled', value: v }; }, function (r) { return { status: 'rejected', reason: r }; });
    }));
  });
  def(Promise, 'any', function (it) {
    return new Promise(function (res, rej) {
      var a = Array.from(it), n = a.length, errs = new Array(n);
      if (!n) return rej(new (window.AggregateError || Error)([], 'All promises were rejected'));
      a.forEach(function (p, i) { Promise.resolve(p).then(res, function (e) { errs[i] = e; if (--n === 0) rej(new (window.AggregateError || Error)(errs, 'All promises were rejected')); }); });
    });
  });

  // ---------- Set (طرق المجموعات) ----------
  var SP = Set.prototype;
  function toSet(x) { return x instanceof Set ? x : new Set(typeof x.keys === 'function' ? x.keys() : x); }
  def(SP, 'union', function (o) { var r = new Set(this); toSet(o).forEach(function (v) { r.add(v); }); return r; });
  def(SP, 'intersection', function (o) { var s = toSet(o), r = new Set(); this.forEach(function (v) { if (s.has(v)) r.add(v); }); return r; });
  def(SP, 'difference', function (o) { var s = toSet(o), r = new Set(); this.forEach(function (v) { if (!s.has(v)) r.add(v); }); return r; });
  def(SP, 'symmetricDifference', function (o) { var s = toSet(o), r = new Set(this); s.forEach(function (v) { if (r.has(v)) r.delete(v); else r.add(v); }); return r; });
  def(SP, 'isSubsetOf', function (o) { var s = toSet(o), ok = true; this.forEach(function (v) { if (!s.has(v)) ok = false; }); return ok; });
  def(SP, 'isSupersetOf', function (o) { var me = this, ok = true; toSet(o).forEach(function (v) { if (!me.has(v)) ok = false; }); return ok; });
  def(SP, 'isDisjointFrom', function (o) { var s = toSet(o), ok = true; this.forEach(function (v) { if (s.has(v)) ok = false; }); return ok; });

  // ---------- structuredClone ----------
  def(window, 'structuredClone', function (v) {
    var seen = new WeakMap();
    function c(x) {
      if (x === null || typeof x !== 'object') { if (typeof x === 'function' || typeof x === 'symbol') throw new DOMException('The object can not be cloned.', 'DataCloneError'); return x; }
      if (seen.has(x)) return seen.get(x);
      var r;
      if (x instanceof Date) return new Date(x.getTime());
      if (x instanceof RegExp) return new RegExp(x.source, x.flags);
      if (x instanceof ArrayBuffer) return x.slice(0);
      if (ArrayBuffer.isView(x)) return new x.constructor(x.buffer.slice(0), x.byteOffset, x.length === undefined ? undefined : x.length);
      if (x instanceof Map) { r = new Map(); seen.set(x, r); x.forEach(function (val, k) { r.set(c(k), c(val)); }); return r; }
      if (x instanceof Set) { r = new Set(); seen.set(x, r); x.forEach(function (val) { r.add(c(val)); }); return r; }
      if (Array.isArray(x)) { r = new Array(x.length); seen.set(x, r); for (var i = 0; i < x.length; i++) r[i] = c(x[i]); return r; }
      if (x instanceof Error) { r = new x.constructor(x.message); seen.set(x, r); return r; }
      r = {}; seen.set(x, r);
      Object.keys(x).forEach(function (k) { r[k] = c(x[k]); });
      return r;
    }
    return c(v);
  });

  // ---------- Web APIs ----------
  try {
    if (window.AbortSignal) {
      def(AbortSignal, 'timeout', function (ms) { var c = new AbortController(); setTimeout(function () { c.abort(new DOMException('signal timed out', 'TimeoutError')); }, ms); return c.signal; });
      def(AbortSignal, 'any', function (list) {
        var c = new AbortController();
        Array.from(list).forEach(function (s) { if (s.aborted) c.abort(s.reason); else s.addEventListener('abort', function () { c.abort(s.reason); }, { once: true }); });
        return c.signal;
      });
    }
  } catch (e) {}
  try {
    if (window.crypto && !crypto.randomUUID) {
      crypto.randomUUID = function () {
        var b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128;
        var h = Array.prototype.map.call(b, function (x) { return (x + 256).toString(16).slice(1); });
        return h.slice(0, 4).join('') + '-' + h.slice(4, 6).join('') + '-' + h.slice(6, 8).join('') + '-' + h.slice(8, 10).join('') + '-' + h.slice(10).join('');
      };
    }
  } catch (e) {}
  def(URL, 'canParse', function (u, b) { try { new URL(u, b); return true; } catch (e) { return false; } });
  def(window, 'queueMicrotask', function (f) { Promise.resolve().then(f); });
  def(window, 'requestIdleCallback', function (f) { var s = Date.now(); return setTimeout(function () { f({ didTimeout: false, timeRemaining: function () { return Math.max(0, 50 - (Date.now() - s)); } }); }, 1); });
  def(window, 'cancelIdleCallback', function (id) { clearTimeout(id); });
  try { def(Element.prototype, 'replaceChildren', function () { while (this.firstChild) this.removeChild(this.firstChild); this.append.apply(this, arguments); }); } catch (e) {}
  try { def(Element.prototype, 'checkVisibility', function () { var r = this.getClientRects(); return !!(r && r.length) && getComputedStyle(this).visibility !== 'hidden'; }); } catch (e) {}

  try {
    if (typeof window.Notification === 'undefined') {
      var Nf = function Notification(title, opts) { this.title = String(title); this.body = (opts && opts.body) || ''; };
      Nf.permission = 'denied';
      Nf.maxActions = 0;
      Nf.requestPermission = function (cb) { var p = Promise.resolve('denied'); if (typeof cb === 'function') p.then(cb); return p; };
      Nf.prototype.close = function () {};
      Nf.prototype.addEventListener = function () {};
      Nf.prototype.removeEventListener = function () {};
      Object.defineProperty(window, 'Notification', { value: Nf, writable: true, configurable: true });
    }
  } catch (e) {}
  try {
    def(window, 'showOpenFilePicker', function (opts) {
      return new Promise(function (res, rej) {
        var i = document.createElement('input');
        i.type = 'file';
        i.multiple = !!(opts && opts.multiple);
        try {
          var acc = [];
          ((opts && opts.types) || []).forEach(function (t) {
            var a = t.accept || {};
            Object.keys(a).forEach(function (m) { acc.push(m); (a[m] || []).forEach(function (x) { acc.push(x); }); });
          });
          if (acc.length) i.accept = acc.join(',');
        } catch (e) {}
        i.style.display = 'none';
        function abort() { try { i.remove(); } catch (e) {} rej(new DOMException('The user aborted a request.', 'AbortError')); }
        i.addEventListener('change', function () {
          var fs = Array.prototype.slice.call(i.files || []);
          try { i.remove(); } catch (e) {}
          if (!fs.length) { abort(); return; }
          res(fs.map(function (f) {
            return { kind: 'file', name: f.name, getFile: function () { return Promise.resolve(f); }, isSameEntry: function (o) { return o === this; } };
          }));
        });
        i.addEventListener('cancel', abort);
        (document.body || document.documentElement).appendChild(i);
        i.click();
      });
    });
  } catch (e) {}
  try {
    var wo = window.open;
    if (typeof wo === 'function') {
      var AUTH = /^https:\/\/(accounts\.google\.com|[^\/]*\.firebaseapp\.com\/__\/auth|login\.microsoftonline\.com|login\.live\.com|appleid\.apple\.com|github\.com\/login\/oauth|[^\/]*\.facebook\.com\/(v[\d.]+\/)?dialog|www\.linkedin\.com\/oauth|api\.twitter\.com\/oauth|twitter\.com\/i\/oauth2|x\.com\/i\/oauth2|discord\.com\/oauth2|[^\/]*\.auth0\.com|[^\/]*\.okta\.com|[^\/]*\.cloudflareaccess\.com|www\.paypal\.com|js\.stripe\.com)/i;
      window.open = function open(u, n, f) {
        try { if (!f && u && AUTH.test(String(u))) f = 'popup=yes,width=500,height=650'; } catch (e) {}
        return wo.call(this, u, n, f);
      };
    }
  } catch (e) {}
})();
