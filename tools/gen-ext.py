#!/usr/bin/env python3
"""يولّد سكربتات عالم الصفحة (MAIN world) لإضافة Gecko من tools/src/*.js.
الحقن عبر scripting.registerContentScripts يتجاوز CSP الصفحة (الحقن بوسم <script> مضمّن كان يُحجب).
العلامات (__FIT__ …) تُستبدل بقراءة window.__semoCfg التي تضبطها ملفات cfg-*.js الصغيرة حسب الإعدادات.
الاستخدام:  python3 tools/gen-ext.py   (شغّله بعد أي تعديل على tools/src/*.js المذكورة)"""
import re, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
A = ROOT / "tools/src"
E = ROOT / "app/src/main/assets/extensions/semo"
trusted = json.dumps(["firebase.google.com", "firebaseapp.com", "web.app", "firebaseio.com", "cloud.google.com", "console.cloud.google.com", "cloudflare.com", "cloudflareaccess.com", "pages.dev", "workers.dev", "cloudflarestorage.com", "github.com", "github.dev", "gitlab.com", "bitbucket.org", "vercel.com", "vercel.app", "netlify.com", "netlify.app", "supabase.com", "supabase.co", "render.com", "onrender.com", "heroku.com", "herokuapp.com", "railway.app", "fly.io", "digitalocean.com", "amazon.com", "aws.amazon.com", "amazonaws.com", "azure.com", "azurewebsites.net", "microsoft.com", "microsoftonline.com", "live.com", "office.com", "atlassian.net", "atlassian.com", "slack.com", "notion.so", "figma.com", "stripe.com", "paypal.com", "auth0.com", "okta.com", "apple.com", "facebook.com", "linkedin.com", "x.com", "twitter.com", "discord.com", "openai.com", "chatgpt.com", "claude.ai", "anthropic.com", "huggingface.co", "expo.dev", "mongodb.com", "neon.tech", "sentry.io", "datadoghq.com", "codepen.io", "jsfiddle.net", "stackblitz.com", "codesandbox.io", "replit.com", "glitch.com", "glitch.me", "google.com", "googleusercontent.com", "gstatic.com"])

C = "(window.__semoCfg||{})"
SUBS = {
    "__FIT__": f"(!!{C}.fit)", "__LAZY__": f"(!!{C}.lazy)", "__BOOST__": f"(!!{C}.boost)", "__POP__": f"(!!{C}.pop)",
    "__ADS__": f"(!!{C}.ytads)", "__TRUSTED__": trusted,
    "__EXTRAS__": f"({{zoom:!!{C}.zoom,copy:!!{C}.copy,draft:!!{C}.draft}})",
}
MAIN = ["polyfills", "webcompat", "langfix", "enhance", "perfboost", "shield", "cosmetic", "smooth", "extras", "ytads"]
FLAGS = ["fit", "lazy", "boost", "pop", "zoom", "copy", "draft", "ytads"]

for n in MAIN:
    s = (A / f"{n}.js").read_text(encoding="utf-8")
    for k, v in SUBS.items():
        s = s.replace(k, v)
    left = re.findall(r"__[A-Z]+__", s)
    left = [x for x in left if x not in ("__NEXT_DATA__", "__NUXT__")]
    if left: raise SystemExit(f"{n}.js: علامات غير مستبدلة {left}")
    (E / f"main-{n}.js").write_text("try{(function(){\n" + s + "\n}).call(window)}catch(e){}\n", encoding="utf-8")
for f in FLAGS:
    (E / f"cfg-{f}.js").write_text(f'(window.__semoCfg=window.__semoCfg||{{}}).{f}=true;\n', encoding="utf-8")
print("ok:", len(MAIN), "scripts,", len(FLAGS), "flags")
