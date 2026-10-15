#!/bin/sh
# Shrinks a downloaded model for the web: textures to 1024 px, then WebP (three.js reads EXT_texture_webp), then
# unused data out. Neither Draco nor Meshopt, which the production CSP does not allow (see frontend/README.md).
# An optional third argument keeps only that fraction of the triangles (0.1 = a tenth): for what is seen from afar
# or in part, such as a desk of which only the top is in view. Simplifying happens here, so the file stays plain glTF.
# An optional fourth argument is the texture size in px (default 1024): 2048 for a face, which is seen close up.
#
#   sh tools/desk-assets/optimize-props.sh design/generated/desk/props/books.glb frontend/src/assets/desk/props/books.glb
#   sh tools/desk-assets/optimize-props.sh design/generated/desk/props/desk.glb frontend/src/assets/desk/props/desk.glb 0.1
#   sh tools/desk-assets/optimize-props.sh design/generated/desk/props/person.glb frontend/src/assets/desk/props/person.glb 0.5 2048
set -e
tmp=$(mktemp -d)
cli="npx --yes @gltf-transform/cli@4.5.1"
size="${4:-1024}"
$cli resize "$1" "$tmp/r.glb" --width "$size" --height "$size"
$cli webp "$tmp/r.glb" "$tmp/w.glb" --quality 82
if [ -n "$3" ]; then
  $cli simplify "$tmp/w.glb" "$tmp/s.glb" --ratio "$3" --error 0.01
  mv "$tmp/s.glb" "$tmp/w.glb"
fi
$cli prune "$tmp/w.glb" "$2"
rm -rf "$tmp"
