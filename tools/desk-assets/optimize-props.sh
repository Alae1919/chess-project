#!/bin/sh
# Shrinks a downloaded model for the web: textures to 1024 px, then WebP (three.js reads EXT_texture_webp), then
# unused data out. Neither Draco nor Meshopt, which the production CSP does not allow (see frontend/README.md).
#
#   sh tools/desk-assets/optimize-props.sh design/generated/desk/props/stack_of_old_books.glb frontend/src/assets/desk/props/books.glb
set -e
tmp=$(mktemp -d)
npx --yes @gltf-transform/cli@4.5.1 resize "$1" "$tmp/r.glb" --width 1024 --height 1024
npx --yes @gltf-transform/cli@4.5.1 webp "$tmp/r.glb" "$tmp/w.glb" --quality 82
npx --yes @gltf-transform/cli@4.5.1 prune "$tmp/w.glb" "$2"
rm -rf "$tmp"
