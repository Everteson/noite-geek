"""Le a sua conta da Crunchyroll (Continuar assistindo e a sua lista) com os cookies do navegador.

Usa o mesmo acesso que o site crunchyroll.com usa por dentro (nao e uma API oficial): troca o
cookie de login (etp_rt) por um token de 5 minutos e le duas listas. So leitura.
Se o site devolver um etp_rt novo, ele e gravado de volta no arquivo de cookies.
"""
import base64
import re
from http.cookiejar import Cookie, MozillaCookieJar

BASE = "https://www.crunchyroll.com"
CLIENT_DEFAULT = "noaihdevm_6iyg0a8l0q"  # client id publico do site (lido da pagina se mudar)


def _session(path):
    from curl_cffi import requests

    jar = MozillaCookieJar(path)
    jar.load(ignore_discard=True, ignore_expires=True)
    s = requests.Session(impersonate="chrome")
    for c in jar:
        if "crunchyroll" in c.domain:
            s.cookies.set(c.name, c.value, domain=".crunchyroll.com")
    return s, jar


def _save_rotated(s, jar, path, log):
    """Se o site mandou um etp_rt novo, grava no arquivo (senao o antigo para de valer)."""
    new = None
    for c in s.cookies.jar:
        if c.name == "etp_rt" and c.value:
            new = c
    if new is None:
        return
    old = [c for c in jar if c.name == "etp_rt"]
    if old and old[0].value == new.value:
        return
    base = old[0] if old else None
    for c in old:
        jar.clear(c.domain, c.path, c.name)
    jar.set_cookie(Cookie(0, "etp_rt", new.value, None, False, ".crunchyroll.com", True, True, "/", True,
                          True, new.expires or (base.expires if base else None), False, None, None, {}))
    jar.save(ignore_discard=True, ignore_expires=True)
    log("crunchyroll: login renovado e salvo")


def _client_id(s):
    try:
        html = s.get(BASE + "/", timeout=20).text
        m = re.search(r'accountAuthClientId"?\s*[:=]\s*"([a-z0-9_\-]{10,40})"', html)
        if m:
            return m.group(1)
    except Exception:
        pass
    return CLIENT_DEFAULT


def _image(panel, want=600):
    th = (panel.get("images") or {}).get("thumbnail") or []
    sizes = th[0] if th and isinstance(th[0], list) else th
    best = None
    for i in sorted(sizes, key=lambda i: i.get("width", 0)):
        best = i.get("source")
        if i.get("width", 0) >= want:
            break
    best = best or ""
    i = best.find("/catalog/")
    if i >= 0:  # mesma imagem em JPEG 400x225 (~15 KB em vez de ~250 KB em PNG)
        best = ("https://imgsrv.crunchyroll.com/cdn-cgi/image/fit=cover,format=jpeg,quality=80,"
                "width=400,height=225" + best[i:])
    return best


def _item(x, kind):
    p = x.get("panel") or {}
    m = p.get("episode_metadata") or {}
    if p.get("type") != "episode" or not p.get("id"):
        return None
    dur = int(m.get("duration_ms") or 0) // 1000
    head = int(x.get("playhead") or 0)
    if x.get("fully_watched"):
        head = 0  # ja viu: na lista ele aponta o proximo, sem barra
    prog = round(head / dur, 3) if dur and head > 20 else 0
    se, ep = m.get("season_number"), m.get("episode_number")
    label = ("T%s E%s" % (se, ep)) if se and ep else (("Ep. %s" % ep) if ep else "")
    title = (p.get("title") or "").strip()
    sub = label + (" · " + title if title and not re.fullmatch(r"Epis.dio \d+", title) else "")
    badge = "Novo" if x.get("new") else ""
    if kind == "cont" and prog:
        left = max(1, (dur - head) // 60)
        badge = "%d min restantes" % left
    return {
        "id": p["id"],
        "series": m.get("series_title") or p.get("title") or "",
        "series_id": m.get("series_id") or "",
        "sub": sub,
        "thumb": _image(p),
        "progress": prog,
        "badge": badge,
    }


def fetch(path, log, n_cont=20, n_list=30):
    s, jar = _session(path)
    auth = base64.b64encode((_client_id(s) + ":").encode()).decode()
    dev = next((c.value for c in jar if c.name == "device_id"), "")
    r = s.post(BASE + "/auth/v1/token", headers={"Authorization": "Basic " + auth},
               data={"grant_type": "etp_rt_cookie", "device_id": dev, "device_type": "Chrome on Windows"},
               timeout=20)
    if r.status_code != 200:
        raise RuntimeError("login recusado (%d): exporte os cookies da Crunchyroll de novo" % r.status_code)
    _save_rotated(s, jar, path, log)
    t = r.json()
    h = {"Authorization": "Bearer " + t["access_token"]}
    acc = t["account_id"]
    out = {}
    for key, url, n, kind in (
            ("cr_cont", "/content/v2/discover/%s/history" % acc, n_cont, "cont"),
            ("cr_list", "/content/v2/discover/%s/watchlist" % acc, n_list, "list")):
        try:
            r = s.get(BASE + url, headers=h, params={"n": n * 2, "locale": "pt-BR"}, timeout=20)
            r.raise_for_status()
            items, seen = [], set()
            for x in r.json().get("data") or []:
                if kind == "cont" and x.get("fully_watched"):
                    continue
                it = _item(x, kind)
                if it and it["series_id"] not in seen:
                    seen.add(it["series_id"])
                    items.append(it)
                if len(items) >= n:
                    break
            out[key] = items
            log("%s: %d episodios" % (key, len(items)))
        except Exception as ex:
            log("%s falhou: %s" % (key, ex))
            out[key] = []
    return out
