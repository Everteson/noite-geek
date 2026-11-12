#!/usr/bin/env bash
# Gera os papeis de parede (fundo da tela inteira) por periodo com o Codex. So os que faltam.
cd "$(dirname "$0")/fundo"
BASE="Wide 16:9 anime background art (wallpaper), no people, no characters. Panoramic view of a harbor city with orange cargo cranes, calm water and a skyline, seen from a high balcony. The scene must work as a TV home-screen wallpaper: soft, slightly painterly, low detail and low contrast in the lower two thirds (water, quay and soft shadows) so white text and thumbnails stay readable over it; the most interesting detail (sky, skyline, cranes) sits in the upper third and on the right side. Cinematic lighting, no text, no letters, no logos, no watermark."
declare -A P
P[manha]="Sunrise: soft pink, peach and gold sky, light mist over the water, gentle warm light."
P[tarde]="Late afternoon golden hour: orange and violet sky, long warm reflections on the water, city lights just starting."
P[noite]="Night: deep blue and purple sky, neon city lights in purple, blue and orange reflected on wet ground and water, a few stars."
P[madrugada]="Around 3 a.m.: big full moon over a quiet dark harbor, very dim navy blue palette, a few distant lights, peaceful and still."
for k in noite manha tarde madrugada; do
  [ -s "$k.png" ] && { echo "ja existe $k"; continue; }
  echo ">> gerando $k"
  codex exec --skip-git-repo-check -s workspace-write -C "$(pwd -W)" "Use your image generation tool to create ONE image and save it as a PNG named $k.png in the current folder. $BASE ${P[$k]} After saving, print the absolute path." > "../log-fundo-$k.txt" 2>&1
  [ -s "$k.png" ] && echo "ok $k" || echo "FALHOU $k"
done
echo FIM
