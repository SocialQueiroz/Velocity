#!/usr/bin/env bash
# Roda o teste visual sem monitor (Xvfb + Mesa por software) e copia as screenshots
# para build/visualtest/. Uso: art/tools/visual_test.sh   (na raiz do repositório)
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p run/saves build/visualtest
if [ ! -d run/saves/vt ]; then
  # Gera um mundo plano criativo usando o servidor dedicado e copia como save de singleplayer.
  echo "eula=true" > run/eula.txt
  printf 'online-mode=false\nlevel-type=minecraft\\:flat\ngamemode=creative\ngenerate-structures=false\nspawn-protection=0\nlevel-name=saves/vt\n' > run/server.properties
  ( for i in $(seq 1 200); do sleep 3; grep -q "Done (" run/logs/latest.log 2>/dev/null && break; done; echo stop ) \
    | ./gradlew runServer --console=plain >/dev/null 2>&1 || true
fi
# Sem tela de acessibilidade do 1º uso, sem pausa ao perder foco, sem tutorial.
touch run/options.txt
for kv in onboardAccessibility:false pauseOnLostFocus:false tutorialStep:none renderDistance:6 joinedFirstServer:true; do
  k=${kv%%:*}; grep -v "^$k:" run/options.txt > run/options.tmp || true; echo "$kv" >> run/options.tmp; mv run/options.tmp run/options.txt
done
rm -f run/screenshots/vt_*.png
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5
timeout 540 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runVisualTest --console=plain 2>&1 \
  | grep -E "VISUALTEST|VELOCITY|ERROR|Exception|Modelo de" | grep -v JAVA_TOOL || true
cp run/screenshots/vt_*.png build/visualtest/ 2>/dev/null || true
ls build/visualtest
