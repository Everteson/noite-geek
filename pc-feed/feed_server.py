"""Noite Geek - feed do YouTube e da Crunchyroll para a TV.

Roda no PC de casa. A cada REFRESH_MIN minutos le a sua conta do YouTube com o yt-dlp
(recomendados, inscricoes e historico) usando os cookies do navegador, e entrega um JSON
pequeno so pra rede de casa:

    GET http://<ip do pc>:8787/feed.json?token=<token>

Config em config.json (criado na primeira execucao):
    token        senha que a TV manda (gerada sozinha)
    port         porta (8787)
    cookies      caminho de um cookies.txt exportado do navegador, OU
    browser      nome do navegador para o yt-dlp ler direto (firefox funciona; chrome/edge no Windows nao)
    crunchyroll_cookies  cookies.txt exportado do crunchyroll.com ("" desliga a Crunchyroll)
    yt_channel   canal do YouTube a usar quando a conta tem varios (ex.: "@seu_canal"; "" = o ativo)
    refresh_min  minutos entre atualizacoes (40)
    allow        prefixo de IP que pode acessar ("192.168.1.")
"""
import json
import os
import secrets
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.join(HERE, "config.json")
CACHE = os.path.join(HERE, "feed.json")
LOG = os.path.join(HERE, "feed.log")

FEEDS = {  # chave no JSON -> feed do yt-dlp, quantos itens
    "rec": (":ytrec", 30),
    "subs": (":ytsubs", 30),
    "hist": (":ythis", 12),
}


def log(msg):
    line = time.strftime("%Y-%m-%d %H:%M:%S ") + msg
    print(line, flush=True)
    try:
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line + "\n")
        if os.path.getsize(LOG) > 512 * 1024:
            os.replace(LOG, LOG + ".old")
    except OSError:
        pass


def load_config():
    cfg = {"token": "", "port": 8787, "cookies": "cookies.txt", "browser": "",
           "refresh_min": 40, "allow": "192.168.1.",
           "crunchyroll_cookies": "cookies_crunch.txt", "yt_channel": ""}
    if os.path.exists(CONFIG):
        with open(CONFIG, encoding="utf-8") as f:
            cfg.update(json.load(f))
    if not cfg["token"]:
        cfg["token"] = secrets.token_urlsafe(18)
        with open(CONFIG, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
    return cfg


def video(e):
    vid = e.get("id")
    if not vid or len(vid) != 11:
        return None
    return {
        "id": vid,
        "title": (e.get("title") or "").strip(),
        "channel": e.get("channel") or e.get("uploader") or "",
        "duration": int(e.get("duration") or 0),
        "thumb": "https://i.ytimg.com/vi/%s/mqdefault.jpg" % vid,
    }


def use_channel(opts, handle):
    """Deixa ativo o canal pedido (como trocar de canal no site). O YouTube guarda a escolha
    na sessao dos cookies; o yt-dlp grava os cookies atualizados de volta no arquivo."""
    import yt_dlp

    want = handle if handle.startswith("@") else "@" + handle

    def accounts(y):
        t = y.urlopen("https://www.youtube.com/getAccountSwitcherEndpoint").read().decode("utf-8", "replace")
        found = []

        def walk(o):
            if isinstance(o, dict):
                if "accountItem" in o:
                    a = o["accountItem"]
                    url = None
                    ep = a.get("serviceEndpoint", {}).get("selectActiveIdentityEndpoint", {})
                    for tk in ep.get("supportedTokens", []):
                        if "accountSigninToken" in tk:
                            url = tk["accountSigninToken"].get("signinUrl")
                    h = a.get("channelHandle", {}).get("simpleText", "")
                    found.append((h.lower(), bool(a.get("isSelected")), url))
                for v in o.values():
                    walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)

        walk(json.loads(t[t.find("{"):]))
        return found

    with yt_dlp.YoutubeDL({k: v for k, v in opts.items() if k in ("cookiefile", "cookiesfrombrowser")}
                          | {"quiet": True}) as y:
        acc = accounts(y)
        mine = [a for a in acc if a[0] == want.lower()]
        if not mine:
            raise RuntimeError("canal %s nao esta nesta conta (tem: %s)" % (want, ", ".join(a[0] for a in acc)))
        if mine[0][1]:
            return
        y.urlopen("https://www.youtube.com" + mine[0][2]).read()
        if not [a for a in accounts(y) if a[0] == want.lower() and a[1]]:
            raise RuntimeError("o YouTube nao aceitou a troca pra %s" % want)
        log("youtube: canal trocado pra %s" % want)


def fetch(cfg):
    import yt_dlp

    opts = {"extract_flat": "in_playlist", "quiet": True, "no_warnings": True,
            "skip_download": True, "ignoreerrors": True}
    cookies = cfg.get("cookies") or ""
    if cookies and not os.path.isabs(cookies):
        cookies = os.path.join(HERE, cookies)
    if cfg.get("browser"):
        opts["cookiesfrombrowser"] = (cfg["browser"],)
    elif os.path.exists(cookies):
        opts["cookiefile"] = cookies
    else:
        raise RuntimeError("sem cookies: coloque cookies.txt em %s ou defina 'browser'" % HERE)

    if cfg.get("yt_channel"):
        try:
            use_channel(opts, cfg["yt_channel"])
        except Exception as ex:
            log("troca de canal falhou: %s" % ex)

    out = {"updated": int(time.time())}
    for key, (url, n) in FEEDS.items():
        o = dict(opts, playlistend=n)
        try:
            with yt_dlp.YoutubeDL(o) as ydl:
                info = ydl.extract_info(url, download=False) or {}
            items, seen = [], set()
            for e in info.get("entries") or []:
                v = video(e or {})
                if v and v["id"] not in seen and v["title"]:
                    seen.add(v["id"])
                    items.append(v)
            out[key] = items
            log("%s: %d videos" % (key, len(items)))
        except Exception as ex:  # um feed falhar nao derruba os outros
            log("%s falhou: %s" % (key, ex))
            out[key] = []
    return out


class Feed:
    def __init__(self):
        self.lock = threading.Lock()
        self.body = b'{"updated":0,"rec":[],"subs":[],"hist":[]}'
        if os.path.exists(CACHE):
            with open(CACHE, "rb") as f:
                self.body = f.read()

    def refresh(self, cfg):
        try:
            data = fetch(cfg)
        except Exception as ex:
            log("youtube falhou: %s" % ex)
            data = {"updated": int(time.time())}
        if not any(data.get(k) for k in FEEDS):
            log("youtube: nada veio (cookies vencidos?); mantendo a lista anterior")
        cr = cfg.get("crunchyroll_cookies") or ""
        if cr and not os.path.isabs(cr):
            cr = os.path.join(HERE, cr)
        if cr and os.path.exists(cr):
            try:
                import importlib.util  # roda com -I: carrega o crunchy.py desta pasta pelo caminho
                spec = importlib.util.spec_from_file_location("crunchy", os.path.join(HERE, "crunchy.py"))
                crunchy = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(crunchy)
                data.update(crunchy.fetch(cr, log))
            except Exception as ex:
                log("crunchyroll falhou: %s" % ex)
        # lista que veio vazia fica com a versao anterior (cookie vencido nao apaga a fileira)
        try:
            old = json.loads(self.body.decode("utf-8"))
        except ValueError:
            old = {}
        for k, v in old.items():
            if k != "updated" and not data.get(k):
                data[k] = v
        body = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        tmp = CACHE + ".tmp"
        with open(tmp, "wb") as f:
            f.write(body)
        os.replace(tmp, CACHE)
        with self.lock:
            self.body = body


def main():
    cfg = load_config()
    feed = Feed()

    def loop():
        while True:
            try:
                feed.refresh(cfg)
            except Exception:
                log("erro na atualizacao:\n" + traceback.format_exc())
            time.sleep(max(15, int(cfg["refresh_min"])) * 60)

    if "--once" in sys.argv:
        feed.refresh(cfg)
        print(feed.body[:400])
        return

    threading.Thread(target=loop, daemon=True).start()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            ip = self.client_address[0]
            u = urlparse(self.path)
            token = (parse_qs(u.query).get("token") or [""])[0]
            if not (ip.startswith(cfg["allow"]) or ip == "127.0.0.1"):
                return self.send_error(403)
            if u.path != "/feed.json" or not secrets.compare_digest(token, cfg["token"]):
                return self.send_error(404)
            with feed.lock:
                body = feed.body
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            pass

    log("servindo na porta %d" % cfg["port"])
    ThreadingHTTPServer(("0.0.0.0", int(cfg["port"])), Handler).serve_forever()


if __name__ == "__main__":
    main()
