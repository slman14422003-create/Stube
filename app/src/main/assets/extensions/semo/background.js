const hosts = new Set();
// إعدادات التطبيق (Prefs) تصل عبر المنفذ الأصلي وتُحفظ ليعمل الحقن فور بدء التشغيل
const DEFAULT_CFG = { shield: true, ads: true, ytads: true, fit: true, lazy: true, boost: true, pop: true, zoom: true, copy: true, draft: true, dataSaver: false };
let cfg = Object.assign({}, DEFAULT_CFG);
fetch(browser.runtime.getURL("blocklist.txt"))
  .then(r => r.text())
  .then(t => {
    for (const line of t.split("\n")) {
      const s = line.trim().toLowerCase();
      if (s && s[0] !== "#") hosts.add(s);
    }
  })
  .catch(() => {});

function listed(h) {
  while (h) {
    if (hosts.has(h)) return true;
    const i = h.indexOf(".");
    if (i < 0) return false;
    h = h.slice(i + 1);
  }
  return false;
}

function baseOf(h) {
  const p = h.split(".");
  return p.length <= 2 ? h : p.slice(-2).join(".");
}

const verdict = new Map();

function hostFast(u) {
  const a = u.indexOf("//");
  if (a < 0) return "";
  let b = u.indexOf("/", a + 2);
  if (b < 0) b = u.length;
  let h = u.slice(a + 2, b);
  const at = h.lastIndexOf("@");
  if (at >= 0) h = h.slice(at + 1);
  const c = h.indexOf(":");
  if (c >= 0) h = h.slice(0, c);
  return h.toLowerCase();
}

browser.webRequest.onBeforeRequest.addListener(
  d => {
    try {
      if (cfg.dataSaver && (d.type === "image" || d.type === "imageset")) return { cancel: true };
      if (!cfg.ads) return {};
      const host = hostFast(d.url);
      if (!host) return {};
      const doc = d.documentUrl || d.originUrl || "";
      const key = host + "|" + doc.slice(0, 80);
      let v = verdict.get(key);
      if (v === undefined) {
        v = false;
        if (listed(host)) {
          const dh = doc ? hostFast(doc) : "";
          v = !(dh && baseOf(dh) === baseOf(host));
        }
        if (verdict.size > 4000) verdict.clear();
        verdict.set(key, v);
      }
      return v ? { cancel: true } : {};
    } catch (e) {
      return {};
    }
  },
  { urls: ["http://*/*", "https://*/*"], types: ["script", "sub_frame", "image", "imageset", "xmlhttprequest", "ping", "beacon", "media", "object", "stylesheet", "font", "websocket", "other"] },
  ["blocking"]
);

let nativePort = null;
let nativeRetry = 0;
let ytCfg = null;
let applyChain = Promise.resolve();

function nativeSchedule() {
  nativeRetry = Math.min(nativeRetry + 1, 10);
  setTimeout(nativeConnect, 500 * nativeRetry);
}

function nativeReply(p, msg) {
  try {
    p.postMessage(msg);
  } catch (e) {}
}

function nativeConnect() {
  let p;
  try {
    p = browser.runtime.connectNative("semo");
  } catch (e) {
    nativeSchedule();
    return;
  }
  nativePort = p;
  p.onDisconnect.addListener(() => {
    if (nativePort === p) nativePort = null;
    nativeSchedule();
  });
  p.onMessage.addListener(m => {
    if (!m || typeof m !== "object") return;
    if (m.type === "cookies") {
      browser.cookies
        .getAll({ url: m.url })
        .then(list => nativeReply(p, { type: "cookies", id: m.id, header: list.map(c => c.name + "=" + c.value).join("; ") }))
        .catch(() => nativeReply(p, { type: "cookies", id: m.id, header: "" }));
    } else if (m.type === "cfg") {
      cfg = Object.assign({}, DEFAULT_CFG, m.cfg || {});
      try { browser.storage.local.set({ cfg }).catch(() => {}); } catch (e) {}
      applyMain();
    } else if (m.type === "fill") {
      browser.tabs.sendMessage(m.tab, { pwFill: true, host: m.host, u: m.u, p: m.p }, { frameId: 0 }).catch(() => {});
    } else if (m.type === "ytcfg") {
      ytCfg = m.cfg || null;
      if (Array.isArray(m.tabs)) {
        for (const id of m.tabs) {
          browser.tabs.sendMessage(id, { ytcfg: ytCfg }, { frameId: 0 }).catch(() => {});
        }
      }
    } else if (m.type === "exec") {
      if (m.file !== "summarize.js" && m.file !== "reader.js") {
        nativeReply(p, { type: "execres", id: m.id, result: null });
      } else {
        browser.tabs
          .executeScript(m.tab, { file: m.file, runAt: "document_idle" })
          .then(r => nativeReply(p, { type: "execres", id: m.id, result: r && r.length ? r[r.length - 1] : null }))
          .catch(() => nativeReply(p, { type: "execres", id: m.id, result: null }));
      }
    } else if (m.type === "ytcall") {
      browser.tabs
        .sendMessage(m.tab, { ytcall: true, fn: m.fn, args: m.args }, { frameId: 0 })
        .catch(() => {});
    }
  });
  nativeRetry = 0;
  nativeReply(p, { type: "hello", ua: navigator.userAgent });
}

nativeConnect();

browser.runtime.onMessage.addListener((m, sender) => {
  if (!m || typeof m !== "object") return;
  if (m.ytcfg) return Promise.resolve(ytCfg);
  if (!sender || !sender.tab || sender.frameId !== 0 || !nativePort) return;
  if (m.page && typeof m.page === "object") {
    nativeReply(nativePort, { type: "page", tab: sender.tab.id, url: sender.url, o: m.page });
  } else if (m.pw) {
    nativeReply(nativePort, { type: "pw", tab: sender.tab.id, url: sender.url, o: m.pw });
  } else if (typeof m.yt === "string" && m.yt.length <= 6000) {
    nativeReply(nativePort, { type: "yt", tab: sender.tab.id, url: sender.url, s: m.yt });
  }
});

const YT_MATCHES = ["https://m.youtube.com/*", "https://www.youtube.com/*", "https://youtube.com/*"];
const YT_ID = "semo-yt-main";

async function registerYtMain() {
  try {
    if (!browser.scripting || !browser.scripting.registerContentScripts) return;
    try {
      await browser.scripting.unregisterContentScripts({ ids: [YT_ID] });
    } catch (e) {}
    await browser.scripting.registerContentScripts([
      {
        id: YT_ID,
        matches: YT_MATCHES,
        js: ["yt-main.js"],
        runAt: "document_start",
        world: "MAIN",
        allFrames: false,
        persistAcrossSessions: false
      }
    ]);
  } catch (e) {}
}

registerYtMain();

// ---------- سكربتات عالم الصفحة (MAIN world) ----------
// تُسجَّل كملفات (لا وسم <script> مضمّن) فلا تحجبها سياسة CSP للصفحة. ملفات cfg-*.js تضبط window.__semoCfg قبل السكربتات.
const MAIN_ID = "semo-main";
const MAIN_TOP_ID = "semo-main-top";
const FLAGS = ["fit", "lazy", "boost", "pop", "zoom", "copy", "draft", "ytads"];

function cfgFiles() {
  const js = [];
  for (const f of FLAGS) if (cfg[f]) js.push("cfg-" + f + ".js");
  return js;
}

function framesFiles() {
  const js = cfgFiles();
  js.push("main-polyfills.js", "main-webcompat.js");
  if (cfg.shield) js.push("main-shield.js");
  return js;
}

function topFiles() {
  const js = cfgFiles();
  js.push("main-langfix.js", "main-enhance.js", "main-perfboost.js");
  if (cfg.ads) js.push("main-cosmetic.js");
  if (cfg.fit || cfg.lazy || cfg.boost || cfg.pop) js.push("main-smooth.js");
  if (cfg.zoom || cfg.copy || cfg.draft) js.push("main-extras.js");
  if (cfg.ytads) js.push("main-ytads.js");
  return js;
}

async function applyMainNow() {
  try {
    if (!browser.scripting || !browser.scripting.registerContentScripts) return;
    try {
      await browser.scripting.unregisterContentScripts({ ids: [MAIN_ID, MAIN_TOP_ID] });
    } catch (e) {}
    const m = ["http://*/*", "https://*/*"];
    await browser.scripting.registerContentScripts([
      { id: MAIN_ID, matches: m, js: framesFiles(), runAt: "document_start", world: "MAIN", allFrames: true, persistAcrossSessions: false },
      { id: MAIN_TOP_ID, matches: m, js: topFiles(), runAt: "document_start", world: "MAIN", allFrames: false, persistAcrossSessions: false }
    ]);
  } catch (e) {}
}

function applyMain() {
  applyChain = applyChain.then(applyMainNow, applyMainNow);
}

try {
  browser.storage.local
    .get("cfg")
    .then(r => {
      if (r && r.cfg) cfg = Object.assign({}, DEFAULT_CFG, r.cfg);
    })
    .catch(() => {})
    .then(applyMain);
} catch (e) {
  applyMain();
}
