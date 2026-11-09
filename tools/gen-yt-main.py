#!/usr/bin/env python3
"""Regenerates app/src/main/assets/extensions/semo/yt-main.js from tools/src/yt.js (Gecko build)."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parent.parent
a = root / "app/src/main/assets"
cur = (a / "extensions/semo/yt-main.js").read_text(encoding="utf-8")
src = (root / "tools/src/yt.js").read_text(encoding="utf-8")
for k, v in {"__CC__": "__CFG.cc", "__UI__": "__CFG.ui", "__PLAY__": "__CFG.play", "__BG__": "__CFG.bg", "__RULES__": "__CFG.rules"}.items():
    src = src.replace(k, v)
m = re.search(r"(  function boot\(__CFG\) \{\n).*(\n  \}\n  function start\()", cur, re.S)
if not m: sys.exit("yt-main.js layout changed")
(a / "extensions/semo/yt-main.js").write_text(cur[:m.end(1)] + src + cur[m.start(2):], encoding="utf-8")
print("ok")
