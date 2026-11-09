(function () {
  try {
    var MAX_TEXT = 90000;
    var CJK = /[\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af]/;
    var DIAC = /[\u064B-\u065F\u0670\u0640]/g;
    var PREFIX = /^(?:\u0648|\u0641|\u0628|\u0643)?(?:\u0627\u0644|\u0644\u0644)/;
    var SPLIT = /(?<=[.!?\u061F\u2026\u06D4]["'\u00BB\u201D)\]]?)\s+(?=[\p{Lu}\p{Lo}\p{N}"'\u00AB\u201C(\[])/u;
    var NUMERIC = /^\p{N}+$/u;

    function clean(t) { return (t || '').replace(/\s+/g, ' ').trim(); }

    function linkDensity(el) {
      var tl = (el.textContent || '').length || 1, ll = 0, as = el.getElementsByTagName('a');
      for (var i = 0; i < as.length && i < 400; i++) ll += (as[i].textContent || '').length;
      return ll / tl;
    }

    function pick() {
      var art = document.querySelector('article');
      if (art && (art.textContent || '').length > 900) return art;
      var map = new Map(), ps = document.querySelectorAll('p');
      for (var i = 0; i < ps.length && i < 2500; i++) {
        var p = ps[i], t = clean(p.textContent);
        if (t.length < 50 || (p.closest && p.closest('nav,aside,footer,header,form'))) continue;
        var s = 1 + Math.min(t.split(/[,\u060C]/).length, 6) + Math.min(Math.floor(t.length / 100), 4);
        var par = p.parentElement;
        if (par) {
          map.set(par, (map.get(par) || 0) + s);
          var g = par.parentElement;
          if (g) map.set(g, (map.get(g) || 0) + s / 2);
        }
      }
      var best = null, bs = 0;
      map.forEach(function (v, el) {
        if (el === document.body || el === document.documentElement) return;
        v = v * (1 - Math.min(linkDensity(el), 0.9));
        if (v > bs) { bs = v; best = el; }
      });
      return bs >= 12 ? best : null;
    }

    function paragraphs(root) {
      var list = root.querySelectorAll('p,li'), out = [], total = 0;
      for (var i = 0; i < list.length && i < 1800; i++) {
        var el = list[i];
        if (el.closest && el.closest('nav,aside,footer,form,figure,table,[role=navigation]')) continue;
        if (el.tagName === 'LI' && el.querySelector && el.querySelector('p')) continue;
        var t = clean(el.textContent);
        if (t.length < (CJK.test(t) ? 20 : 40)) continue;
        out.push(t);
        total += t.length;
        if (total > MAX_TEXT) break;
      }
      return out;
    }

    function sentences(p) {
      var t = p.replace(/([\u3002\uFF01\uFF1F])\s*/g, '$1\n'), parts = t.split('\n'), out = [];
      for (var i = 0; i < parts.length; i++) {
        if (!parts[i]) continue;
        var sub = parts[i].split(SPLIT);
        for (var k = 0; k < sub.length; k++) {
          var s = sub[k].trim();
          if (s.length >= (CJK.test(s) ? 12 : 30) && s.length <= 700) out.push(s);
        }
      }
      return out;
    }

    function stem(w) {
      if (/[\u0600-\u06FF]/.test(w)) {
        var a = w.replace(DIAC, '').replace(/[\u0623\u0625\u0622]/g, '\u0627').replace(/\u0649/g, '\u064A').replace(/\u0629/g, '\u0647');
        var r = a.replace(PREFIX, '');
        return r.length >= 3 ? r : a;
      }
      if (w.length > 4 && w.charAt(w.length - 1) === 's' && w.charAt(w.length - 2) !== 's') return w.slice(0, -1);
      return w;
    }

    function tok(s, surf) {
      var m = s.toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) || [], out = [];
      for (var i = 0; i < m.length; i++) {
        var w = m[i];
        if (NUMERIC.test(w)) continue;
        if (CJK.test(w)) {
          for (var j = 0; j < w.length - 1; j++) out.push(w.substr(j, 2));
        } else if (w.length > 2) {
          var st = stem(w);
          out.push(st);
          if (surf) {
            var bucket = surf[st] || (surf[st] = Object.create(null));
            bucket[w] = (bucket[w] || 0) + 1;
          }
        }
      }
      return out;
    }

    function uniq(a) {
      var seen = Object.create(null), out = [];
      for (var i = 0; i < a.length; i++) if (!seen[a[i]]) { seen[a[i]] = 1; out.push(a[i]); }
      return out;
    }

    function jaccard(a, b) {
      var set = Object.create(null), inter = 0;
      for (var i = 0; i < a.length; i++) set[a[i]] = 1;
      for (var j = 0; j < b.length; j++) if (set[b[j]]) inter++;
      var union = a.length + b.length - inter;
      return union ? inter / union : 0;
    }

    var root = pick();
    var paras = root ? paragraphs(root) : [];
    if (paras.length < 3 && document.body) {
      var alt = paragraphs(document.body);
      if (alt.length > paras.length) paras = alt;
    }
    if (paras.length < 2) return JSON.stringify({ ok: 0 });

    var sents = [];
    for (var pi = 0; pi < paras.length && sents.length < 600; pi++) {
      var ss = sentences(paras[pi]);
      for (var si = 0; si < ss.length; si++) sents.push({ t: ss[si], first: si === 0 });
    }
    var text = paras.join(' ');
    var cjk = (text.match(/[\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af]/g) || []).length;
    var cjkDominant = cjk > text.length * 0.3;
    if (text.length < (cjkDominant ? 150 : 300) || sents.length < 3) return JSON.stringify({ ok: 0 });

    var surf = Object.create(null), N = sents.length, tf = Object.create(null), df = Object.create(null);
    for (var i = 0; i < N; i++) {
      var tk = tok(sents[i].t, surf);
      sents[i].k = tk;
      sents[i].u = uniq(tk);
      for (var a = 0; a < tk.length; a++) tf[tk[a]] = (tf[tk[a]] || 0) + 1;
      for (var b = 0; b < sents[i].u.length; b++) df[sents[i].u[b]] = (df[sents[i].u[b]] || 0) + 1;
    }

    var wt = Object.create(null), keys = [];
    for (var term in tf) {
      if (N >= 8 && df[term] / N > 0.5) continue;
      wt[term] = (1 + Math.log(tf[term])) * Math.log(1 + N / df[term]);
      keys.push(term);
    }

    var h1 = root && root.querySelector ? root.querySelector('h1') : null;
    var title = clean((h1 && h1.textContent) || document.title).slice(0, 160);
    var titleToks = uniq(tok(title, null)), titleSet = Object.create(null);
    for (var ti = 0; ti < titleToks.length; ti++) titleSet[titleToks[ti]] = 1;

    for (var x = 0; x < N; x++) {
      var s = sents[x], sum = 0, hit = 0;
      for (var y = 0; y < s.u.length; y++) {
        if (wt[s.u[y]]) sum += wt[s.u[y]];
        if (titleSet[s.u[y]]) hit++;
      }
      var score = sum / Math.pow(s.k.length || 1, 0.6);
      if (titleToks.length) score *= 1 + 0.4 * Math.min(1, hit / titleToks.length);
      if (x === 0) score *= 1.3; else if (s.first) score *= 1.1;
      s.score = score;
    }

    var want = Math.max(2, Math.min(5, Math.round(N / 8)));
    var order = sents.map(function (_, i) { return i; }).sort(function (p, q) { return sents[q].score - sents[p].score; });
    var chosen = [];
    for (var o = 0; o < order.length && chosen.length < want; o++) {
      var cand = sents[order[o]], dup = false;
      for (var c = 0; c < chosen.length; c++) if (jaccard(cand.u, sents[chosen[c]].u) > 0.5) { dup = true; break; }
      if (!dup) chosen.push(order[o]);
    }
    chosen.sort(function (p, q) { return p - q; });
    var points = chosen.map(function (i) {
      var t = sents[i].t;
      return t.length > 320 ? t.slice(0, 317).replace(/\s+\S*$/, '') + '\u2026' : t;
    });

    var kscore = Object.create(null);
    for (var ki = 0; ki < keys.length; ki++) {
      var kt = keys[ki], rare = df[kt] / N <= 0.3 && df[kt] >= 2 && tf[kt] >= 2;
      kscore[kt] = rare && kt.length >= (CJK.test(kt) ? 2 : 4) ? wt[kt] * Math.min(1, kt.length / 6) : 0;
    }
    keys.sort(function (p, q) { return kscore[q] - kscore[p]; });
    var keywords = [];
    for (var kx = 0; kx < keys.length && keywords.length < 6; kx++) {
      var kk = keys[kx];
      if (!kscore[kk]) break;
      var bucket = surf[kk], bestW = kk, bestN = 0;
      if (bucket) for (var wv in bucket) if (bucket[wv] > bestN) { bestN = bucket[wv]; bestW = wv; }
      keywords.push(bestW);
    }

    var words = text.split(/\s+/).length;
    var minutes = Math.max(1, Math.round(cjkDominant ? text.length / 450 : words / 200));
    var lang = String(document.documentElement.lang || '').toLowerCase().split(/[-_]/)[0];

    return JSON.stringify({ ok: 1, title: title, words: words, minutes: minutes, lang: lang, points: points, keywords: keywords });
  } catch (e) {
    return JSON.stringify({ ok: 0 });
  }
})();
