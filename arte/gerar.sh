#!/usr/bin/env bash
# Gera as artes por horario com o Codex (gera so as que faltam).
cd "$(dirname "$0")/original"
BASE="Wide 16:9 anime illustration, same cozy gamer room by a big window overlooking a harbor city with orange cargo cranes and a dark sports car parked on the quay. A young man with messy black hair, black jacket with orange details and over-ear headphones. Keep the character in the left third; keep the upper middle of the picture calm and uncluttered (sky) so a title can be written over it. Cinematic, detailed, no text, no letters, no logos, no watermark."
declare -A P
P[manha-1]="Early morning sunrise: soft pink, peach and gold sky, light mist over the harbor, he stretches holding a steaming coffee mug, monitors off, warm calm light."
P[manha-2]="Bright morning: clear blue sky with a few clouds, sunlight through the window, he reads on a tablet with a cup of coffee, seagulls over the cranes."
P[tarde-1]="Sunny afternoon: vivid blue sky and golden light, he plays a racing game with a game controller, the sports car gleaming in the sun outside."
P[tarde-2]="Late afternoon golden hour: orange and violet sky, long shadows, he leans back watching an anime on the big monitor, city starting to light up."
P[noite-2]="Night: purple and blue neon city lights reflected on the water, light rain, he plays a game with neon-lit keyboard and PC, glowing orange accents."
P[madrugada-1]="Deep late night, about 3 a.m.: big full moon over a quiet dark harbor, very dim blue light, he sleeps in the gaming chair with headphones around his neck, the monitor softly glowing, peaceful."
P[madrugada-2]="Late night with soft rain on the window, dark navy palette, a few city lights, he watches anime under a blanket with the screen glow on his face, calm and cozy."
for k in manha-1 manha-2 tarde-1 tarde-2 noite-2 madrugada-1 madrugada-2; do
  [ -s "$k.png" ] && { echo "ja existe $k"; continue; }
  echo ">> gerando $k"
  codex exec --skip-git-repo-check -s workspace-write -C "$(pwd -W)" "Use your image generation tool to create ONE image and save it as a PNG named $k.png in the current folder. $BASE ${P[$k]} After saving, print the absolute path." > "../log-$k.txt" 2>&1
  [ -s "$k.png" ] && echo "ok $k" || echo "FALHOU $k"
done
echo FIM
