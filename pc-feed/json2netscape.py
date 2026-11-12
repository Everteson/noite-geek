"""Converte cookies exportados em JSON (extensoes de navegador) para o formato cookies.txt do yt-dlp.
Imprime so os dominios e a contagem, nunca os valores."""
import json, sys, time, collections
src, dst = sys.argv[1], sys.argv[2]
data = json.load(open(src, encoding="utf-8-sig"))
if isinstance(data, dict):
    data = data.get("cookies") or list(data.values())
lines = ["# Netscape HTTP Cookie File"]
count = collections.Counter()
for c in data:
    dom = c.get("domain") or c.get("host") or ""
    if not any(k in dom for k in ("youtube.com", "google.com")):
        continue
    host_only = c.get("hostOnly", not dom.startswith("."))
    if not host_only and not dom.startswith("."):
        dom = "." + dom
    exp = c.get("expirationDate") or c.get("expires") or c.get("expiry") or 0
    try:
        exp = int(float(exp))
    except (TypeError, ValueError):
        exp = 0
    if exp <= 0:
        exp = int(time.time()) + 180 * 86400
    lines.append("\t".join([
        ("#HttpOnly_" if c.get("httpOnly") else "") + dom,
        "FALSE" if host_only else "TRUE",
        c.get("path") or "/",
        "TRUE" if c.get("secure") else "FALSE",
        str(exp),
        c.get("name", ""),
        str(c.get("value", "")),
    ]))
    count[dom] += 1
open(dst, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
print("convertidos:", dict(count))
