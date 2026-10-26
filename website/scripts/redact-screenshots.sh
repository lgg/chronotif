#!/usr/bin/env bash
# Turns raw device screenshots into the blurred, metadata-free images the site publishes.
#
# Raw screenshots contain real people's names, photos and messages, so they live in
# screenshots-raw/ (git-ignored, never copied to dist/). Only the output of this script,
# in public/screens/, is published. Regions are in the raw image's pixel coordinates
# (1280 px wide Xiaomi 17T Pro captures): x1,y1,x2,y2 of each card interior to blur, then
# x1,y1,x2,y2 of each timestamp ("3m", "18m") to restore from the original afterwards, so
# the chronological order stays readable. Keep regions must contain only the time label.
#
# Also crops the install-guide screenshots from screenshots-raw/install-guide/.
#
# Requires ImageMagick 7 (`magick`). Usage: npm run screens
set -euo pipefail
cd "$(dirname "$0")/.."

raw=screenshots-raw/17t-pro-chronotif
out=public/screens
mkdir -p "$out"

# name | source file | regions to blur | timestamps to keep
shots=(
  "panel|Screenshot_2026-10-21-15-05-46-296_app.chronotif.jpg|230,550,1215,785 230,850,1215,1085|868,580,1022,640 940,875,1042,935"
  "lock-new|Screenshot_2026-10-21-14-21-27-190_lockscreen.jpg|230,1055,1215,1220 230,1285,1215,1460 230,1525,1215,1755 230,1820,1215,1980|885,1080,1022,1140 940,1315,1042,1375 940,1547,1042,1607 940,1840,1042,1900"
  "panel-earlier|Screenshot_2026-10-21-15-24-50-906_app.chronotif.jpg|220,750,850,945 220,825,1045,950 1060,775,1205,920 220,1515,1090,1640 220,1760,1090,1890 220,1975,1000,2105 220,2200,880,2330 1060,2205,1205,2335|"
  "lock-earlier|Screenshot_2026-10-21-14-21-39-095_lockscreen.jpg|220,1510,1215,1720 220,1850,1215,2035 220,2120,1125,2262 220,2380,1100,2490|890,1522,1035,1585 1060,1848,1205,1910"
)

for entry in "${shots[@]}"; do
  IFS='|' read -r name file regions keeps <<<"$entry"
  args=("$raw/$file")
  for r in $regions; do
    IFS=',' read -r x1 y1 x2 y2 <<<"$r"
    w=$((x2 - x1)) h=$((y2 - y1))
    args+=(\( +clone -crop "${w}x${h}+${x1}+${y1}" +repage -blur 0x22 \) -geometry "+${x1}+${y1}" -composite)
  done
  for r in $keeps; do
    IFS=',' read -r x1 y1 x2 y2 <<<"$r"
    w=$((x2 - x1)) h=$((y2 - y1))
    # Feathered edges so the restored patch blends into the blurred card.
    args+=(\( "$raw/$file" -crop "${w}x${h}+${x1}+${y1}" +repage
      \( -size "${w}x${h}" xc:black -fill white -draw "rectangle 8,8 $((w - 9)),$((h - 9))" -blur 0x5 \)
      -alpha off -compose CopyOpacity -composite \) -compose Over -geometry "+${x1}+${y1}" -composite)
  done
  magick "${args[@]}" -strip "$out/$name-full.png"
  for width in 480 800; do
    magick "$out/$name-full.png" -resize "${width}x" -quality 82 -define webp:method=6 "$out/$name-$width.webp"
  done
  rm "$out/$name-full.png"
  echo "wrote $out/$name-{480,800}.webp"
done

# Install-guide crops: system settings screens, cropped (crop geometry | optional accent ring
# as x1,y1,x2,y2 in the cropped image | optional regions to blur, same coordinates, for the
# Google account card on Play Store screens).
guide=screenshots-raw/install-guide
crops=(
  "install-denied|Screenshot_2026-10-23-08-59-09-474_com.google.android.permissioncontroller.jpg|1280x1250+0+840|"
  "install-restricted|Screenshot_2026-10-23-08-59-43-236_com.android.settings.jpg|1280x960+0+2130|30,720,1250,900|"
  "install-playprotect-menu|Screenshot_2026-10-23-09-03-52-920_com.android.vending.jpg|1280x1660+0+320|53,1404,1227,1587|55,10,1225,270"
  "install-playprotect-home|Screenshot_2026-10-26-14-18-37-846_com.android.vending.jpg|1280x1180+0+150|1124,18,1276,170|"
  "install-playprotect-settings|Screenshot_2026-10-23-09-04-00-748_com.android.vending.jpg|1280x720+0+140|42,407,1240,636|"
  "install-playprotect-pause|Screenshot_2026-10-26-14-18-42-697_com.android.vending.jpg|1280x1750+0+575|112,1115,1168,1281|"
)

for entry in "${crops[@]}"; do
  IFS='|' read -r name file geometry ring blurs <<<"$entry"
  args=("$guide/$file" -crop "$geometry" +repage)
  for r in $blurs; do
    IFS=',' read -r x1 y1 x2 y2 <<<"$r"
    w=$((x2 - x1)) h=$((y2 - y1))
    args+=(\( +clone -crop "${w}x${h}+${x1}+${y1}" +repage -blur 0x22 \) -geometry "+${x1}+${y1}" -composite)
  done
  if [[ -n $ring ]]; then
    IFS=',' read -r x1 y1 x2 y2 <<<"$ring"
    args+=(-fill none -stroke "#0a84ff" -strokewidth 8 -draw "roundrectangle $x1,$y1 $x2,$y2 56,56")
  fi
  magick "${args[@]}" -strip -resize 640x -quality 82 -define webp:method=6 "$out/$name.webp"
  echo "wrote $out/$name.webp"
done
