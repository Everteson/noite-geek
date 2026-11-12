#!/usr/bin/env bash
# Compila o Noite Geek e instala na TV (Depuracao USB ligada na TV).
# Uso, no Git Bash, dentro desta pasta:  bash instalar.sh   (antes: copie instalar.conf.example pra instalar.conf)
set -e
HERE="$(cd "$(dirname "$0")" && pwd -W)"
# TV_HOST e CLEANYOURTV vem de instalar.conf (copie de instalar.conf.example) ou do ambiente.
[ -f "$HERE/instalar.conf" ] && . "$HERE/instalar.conf"
: "${TV_HOST:?defina o IP da TV em instalar.conf (veja instalar.conf.example)}"
CLEANYOURTV=${CLEANYOURTV:-../cleanyourtv}
case "$CLEANYOURTV" in /*|?:*) CYTV="$CLEANYOURTV" ;; *) CYTV="$HERE/$CLEANYOURTV" ;; esac
[ -f "$CYTV/tools/tv.py" ] || { echo "Nao achei o CleanYourTV em $CYTV (git clone https://github.com/Gathin23/cleanyourtv)"; exit 1; }
export TV_HOST MSYS_NO_PATHCONV=1
export JAVA_HOME=${JAVA_HOME:-"C:/Program Files/Microsoft/jdk-17.0.21.21-hotspot"}
cd "$HERE/app"
./gradlew assembleRelease --no-daemon --console=plain -q
APK="$HERE/app/app/build/outputs/apk/release/app-release.apk"
echo "APK: $(stat -c %s "$APK") bytes"
cd "$CYTV"
tools/venv/Scripts/python -I tools/b64push.py "$APK" /data/local/tmp/noitegeek.apk | tail -1
tools/venv/Scripts/python -I tools/tv.py 'pm install -r /data/local/tmp/noitegeek.apk; rm -f /data/local/tmp/noitegeek.apk; am force-stop com.evert.noitegeek; am start -n com.evert.noitegeek/.MainActivity >/dev/null'
