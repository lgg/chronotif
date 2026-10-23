#!/usr/bin/env bash
# Turns raw device screenshots into the blurred, metadata-free images the site publishes.
#
# Raw screenshots contain real people's names, photos and messages, so they live in
# screenshots-raw/ (git-ignored, never copied to dist/). Only the output of this script,
# in public/screens/, is published. Regions are in the raw image's pixel coordinates
# (1280 px wide Xiaomi 17T Pro captures): x1,y1,x2,y2 of each card interior to blur.
#
# Requires ImageMagick 7 (`magick`). Usage: npm run screens
set -euo pipefail
cd "$(dirname "$0")/.."

raw=screenshots-raw/17t-pro-chronotif
out=public/screens
mkdir -p "$out"

# name | source file | regions to blur
shots=(
  "panel|Screenshot_2026-10-21-15-05-46-296_app.chronotif.jpg|230,550,1215,785 230,850,1215,1085"
  "lock-new|Screenshot_2026-10-21-14-21-27-190_lockscreen.jpg|230,1055,1215,1220 230,1285,1215,1460 230,1525,1215,1755 230,1820,1215,1980"
  "lock-earlier|Screenshot_2026-10-21-14-21-33-285_lockscreen.jpg|230,600,1215,735 230,800,1215,970"
)

for entry in "${shots[@]}"; do
  IFS='|' read -r name file regions <<<"$entry"
  args=("$raw/$file")
  for r in $regions; do
    IFS=',' read -r x1 y1 x2 y2 <<<"$r"
    w=$((x2 - x1)) h=$((y2 - y1))
    args+=(\( +clone -crop "${w}x${h}+${x1}+${y1}" +repage -blur 0x22 \) -geometry "+${x1}+${y1}" -composite)
  done
  magick "${args[@]}" -strip "$out/$name-full.png"
  for width in 480 800; do
    magick "$out/$name-full.png" -resize "${width}x" -quality 82 -define webp:method=6 "$out/$name-$width.webp"
  done
  rm "$out/$name-full.png"
  echo "wrote $out/$name-{480,800}.webp"
done
