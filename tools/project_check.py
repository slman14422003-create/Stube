#!/usr/bin/env python3
"""فحص سريع للمشروع قبل البناء: توازن الأقواس، وجود المكونات في الـ Manifest، واستخدام Prefs.
الاستخدام:  python3 tools/project_check.py
"""
import re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/nova/browser"
errors = []
warnings = []

_TOKEN = re.compile("|".join([
    r'"""[\s\S]*?"""',               # نص خام
    r'"(?:\\.|[^"\\\n])*"',            # نص عادي
    r"/\*[\s\S]*?\*/",                 # تعليق كتلي
    r"//[^\n]*",                       # تعليق سطري
    r"'(?:\\.|[^'\\\n])'",             # محرف
]))

def strip(code: str) -> str:
    """يزيل النصوص والتعليقات بمرور واحد (حتى لا يُفهم // داخل رابط كتعليق)."""
    def repl(m):
        t = m.group(0)
        if t.startswith("//") or t.startswith("/*"): return ""
        return "''" if t.startswith("'") else '""'
    return _TOKEN.sub(repl, code)

# 1) توازن الأقواس
for f in sorted(SRC.rglob("*.*")):
    if f.suffix not in (".kt", ".java"):
        continue
    c = strip(f.read_text(encoding="utf-8"))
    for a, b in ("{}", "()", "[]"):
        if c.count(a) != c.count(b):
            warnings.append(f"{f.name}: تحذير (قد يكون إيجابياً كاذباً) عدم توازن {a}{b} ({c.count(a)} مقابل {c.count(b)})")

# 2) مكونات الـ Manifest موجودة
mf = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for name in re.findall(r'android:name="\.(\w+)"', mf):
    if not any(SRC.rglob(f"{name}.kt")) and not any(SRC.rglob(f"{name}.java")):
        errors.append(f"Manifest يشير إلى .{name} ولا يوجد ملف له")

# 2.5) تدقيق أمني للـ Manifest
if 'android:allowBackup="false"' not in mf: warnings.append("أمان: allowBackup غير معطّل (قد تُسرَّب بيانات عبر النسخ الاحتياطي)")
if 'android:debuggable="true"' in mf: errors.append("أمان: debuggable=true في Manifest")
for m in re.finditer(r"<(?:service|receiver|provider)[^>]*android:exported=\"true\"", mf):
    errors.append("أمان: مكوّن مكشوف exported=true: " + m.group(0)[:60])
if re.search(r"setJavaScriptEnabled|addJavascriptInterface", "".join(f.read_text(encoding="utf-8") for f in SRC.rglob("*.kt"))) and "addJavascriptInterface" in "".join(f.read_text(encoding="utf-8") for f in SRC.rglob("*.kt")):
    warnings.append("أمان: addJavascriptInterface مستخدم — تأكد من حصره بمصادر موثوقة")

# 2.7) ملفات المصدر والإضافة
AS = ROOT / "app/src/main/assets"
TS = ROOT / "tools/src"
EXT = AS / "extensions/semo"
bl = TS / "blocklist.txt"
if not bl.exists(): errors.append("tools/src/blocklist.txt مفقود")
else:
    doms = [l.strip().lower() for l in bl.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    for d in doms:
        if not re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", d): errors.append(f"blocklist: نطاق غير صالح: {d}")
    if len(doms) != len(set(doms)): warnings.append("blocklist: نطاقات مكررة")
for need in ("manifest.json", "background.js", "pw.js", "page.js", "yt-main.js", "yt-relay.js", "reader.js", "summarize.js"):
    if not (EXT / need).exists(): errors.append(f"extensions/semo/{need} مفقود")

# 2.8) كل نص L("...") له ترجمة إنجليزية في I18n.kt
i18n = (SRC / "core" / "I18n.kt").read_text(encoding="utf-8")
keys = set(re.findall(r'^\s+"((?:\\.|[^"\\])*)" to "', i18n, re.M))
used = set()
for f in SRC.rglob("*.kt"):
    if f.name == "I18n.kt": continue
    used |= set(re.findall(r'\bL\("((?:\\.|[^"\\])*)"\)', f.read_text(encoding="utf-8")))
for u in sorted(used - keys): errors.append(f"i18n: لا ترجمة إنجليزية للنص: {u}")
for k in sorted(keys - used): warnings.append(f"i18n: ترجمة غير مستخدمة: {k}")

# 2.96) سكربتات الإضافة: خطأ نحوي واحد يعطّل السكربت كله
import shutil, subprocess
if shutil.which("node"):
    for js in sorted(EXT.glob("*.js")) + sorted(AS.glob("*.js")):
        r = subprocess.run(["node", "--check", str(js)], capture_output=True, text=True)
        if r.returncode != 0:
            first = r.stderr.strip().splitlines()[0][:100] if r.stderr.strip() else ""
            errors.append(f"{js.name}: JavaScript syntax error: {first}")

# 2.97) بقايا المحرك القديم (WebView) يجب ألا تعود
_gone = r"\b(WebView|WebSupport|WebCompat|WebBoost|WebEngine|WebFactory|SiteRules|NovaWebView|PageBrain|YtWeb|YtHub|SafariMode|ScriptBundle|Speculate|CustomTabs)\b"
for f in SRC.rglob("*.kt"):
    t = strip(f.read_text(encoding="utf-8"))
    m = re.search(_gone, t)
    if m: errors.append(f"{f.name}: مرجع إلى {m.group(1)} المحذوف")
    if "android.webkit.WebView" in t or "androidx.webkit" in t: errors.append(f"{f.name}: استيراد WebView")

# 2.9) أسرار وملفات GitHub
for pat in ("*.jks", "*.keystore", "keystore.properties"):
    for f in ROOT.rglob(pat):
        if ".git" not in f.parts and "build" not in f.parts and f.name != "keystore.properties.example":
            errors.append(f"أمان: ملف توقيع داخل المشروع ({f.relative_to(ROOT)}) — لا ترفعه إلى git")
gi = (ROOT / ".gitignore").read_text(encoding="utf-8") if (ROOT / ".gitignore").exists() else ""
for need in ("*.jks", "keystore.properties"):
    if need not in gi: errors.append(f".gitignore ينقصه: {need}")
try:
    import yaml
    for f in (ROOT / ".github").rglob("*.y*ml"):
        try: yaml.safe_load(f.read_text(encoding="utf-8"))
        except Exception as e: errors.append(f"YAML غير صالح: {f.relative_to(ROOT)}: {e}")
except ImportError:
    warnings.append("PyYAML غير مثبّت: تخطّي فحص ملفات YAML")
for wf in (ROOT / ".github/workflows").glob("*.yml"):
    t = wf.read_text(encoding="utf-8")
    if re.search(r"uses:\s*[\w./-]+@(main|master)\b", t): warnings.append(f"{wf.name}: action مثبّت على main/master")
    if "permissions:" not in t: warnings.append(f"{wf.name}: بلا permissions صريحة")


# 2.95) تنظيم الواجهة
if not (SRC / "ui" / "UiLayout.kt").exists(): errors.append("UiLayout.kt مفقود (ملف تنظيم الواجهة)")






# 2.98) يوتيوب على Gecko
if not (TS / "yt.js").exists(): errors.append("tools/src/yt.js مفقود")
if "var yt = false" not in (SRC / "browser" / "BrowserTab.kt").read_text(encoding="utf-8"): errors.append("BrowserTab.yt مفقود")
if not (SRC / "perf" / "Smooth.kt").exists(): errors.append("Smooth.kt مفقود")

# 2.99) string resources (R.string): every reference exists in values and values-ar, both files match, and new code files carry no Arabic
RES = ROOT / "app/src/main/res"
_en = RES / "values" / "strings.xml"
_ar = RES / "values-ar" / "strings.xml"
if _en.exists() and _ar.exists():
    n_en = set(re.findall(r'name="(\w+)"', _en.read_text(encoding="utf-8")))
    n_ar = set(re.findall(r'name="(\w+)"', _ar.read_text(encoding="utf-8")))
    for n in sorted(n_en ^ n_ar): errors.append("strings: '" + n + "' is not present in both values and values-ar")
    _used = set()
    for f in SRC.rglob("*.kt"): _used |= set(re.findall(r"(?<!android\.)R\.string\.(\w+)", f.read_text(encoding="utf-8")))
    for n in sorted(_used - n_en): errors.append("strings: R.string." + n + " is not defined")
    for n in sorted(n_en - _used): warnings.append("strings: unused resource " + n)
else:
    errors.append("strings.xml missing in values or values-ar")
_arabic = re.compile(r"[\u0600-\u06FF]")
for rel in ("core/SmartCalc.java", "core/SmartAssist.kt", "core/Strings.kt"):
    fp = SRC / rel
    if not fp.exists(): errors.append(rel + " missing")
    elif _arabic.search(fp.read_text(encoding="utf-8")): errors.append(rel + ": Arabic text inside code (use res/values-ar/strings.xml)")
for rel in ("summarize.js",):
    fp = AS / rel
    if not fp.exists(): errors.append("assets/" + rel + " missing")
    elif _arabic.search(fp.read_text(encoding="utf-8")): errors.append("assets/" + rel + ": Arabic text inside code (use \\u escapes)")

# 3) كل Prefs.xxx المستخدمة معرّفة
prefs = (SRC / "core" / "Settings.kt").read_text(encoding="utf-8")
defined = set(re.findall(r"(?:va[lr])\s+(\w+)\s*(?:by|=|:)|fun\s+(\w+)\(", prefs))
defined = {x for t in defined for x in t if x}
for f in SRC.rglob("*.kt"):
    for m in set(re.findall(r"\bPrefs\.(\w+)", f.read_text(encoding="utf-8"))):
        if m not in defined and m != "engines":
            errors.append(f"{f.name}: Prefs.{m} غير معرّف")

print("\n".join(warnings + errors) if (warnings or errors) else "OK: لا أخطاء ظاهرة")
if not errors and warnings:
    print("لا أخطاء مانعة")
sys.exit(1 if errors else 0)
