#!/bin/sh
# Turns the chosen pieces of Tom Feldmann's Free Wild Music Pack (CC BY 4.0) into the game's music files:
# loudness balanced to about -20 LUFS, 2 s fades at each end, 112 kbps stereo MP3, the one format every platform plays.
#
#   sh assets/audio/branchworld/convert_music.sh
#
# Reads the WAVs from assets/audio/tom-feldmann-free-wild-music/ (any subfolder) and writes
# shared/src/commonMain/composeResources/files/music/. Prints each piece's length.
set -eu

root=$(cd "$(dirname "$0")/../../.." && pwd)
source="$root/assets/audio/tom-feldmann-free-wild-music"
target="$root/shared/src/commonMain/composeResources/files/music"
mkdir -p "$target"

convert() { # <pack name> <game file>
    input=$(find "$source" -iname "$1.wav" | head -n 1)
    if [ -z "$input" ]; then
        echo "Missing $1.wav under $source" >&2
        exit 1
    fi
    length=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$input")
    fade_out=$(awk "BEGIN { print $length - 2 }")
    # Two-pass loudness: measure first, then apply one linear gain, so every piece lands at the same level.
    measured=$(ffmpeg -hide_banner -nostats -i "$input" -af "loudnorm=I=-20:TP=-1.5:LRA=11:print_format=json" \
        -f null - 2>&1 | sed -n '/^{/,/^}/p')
    value() { printf '%s' "$measured" | sed -n "s/.*\"$1\" : \"\([^\"]*\)\".*/\1/p"; }
    loudnorm="loudnorm=I=-20:TP=-1.5:LRA=11:linear=true:measured_I=$(value input_i):measured_TP=$(value input_tp)"
    loudnorm="$loudnorm:measured_LRA=$(value input_lra):measured_thresh=$(value input_thresh):offset=$(value target_offset)"
    ffmpeg -hide_banner -loglevel error -y -i "$input" \
        -af "$loudnorm,afade=t=in:d=2,afade=t=out:st=$fade_out:d=2" \
        -ar 44100 -ac 2 -c:a libmp3lame -b:a 112k -map_metadata -1 "$target/$2"
    printf '%-18s %6.1f s  %s\n' "$1" "$length" "$(du -h "$target/$2" | cut -f1)"
}

convert Plains2 plains.mp3
convert Village3 village.mp3
convert MysticalForest1 mystical-forest.mp3
