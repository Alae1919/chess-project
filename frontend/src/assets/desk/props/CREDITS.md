# Models on the desk

Every model here was generated from reference pictures (`design/refs/desk/props/`) and is the project's own: the
lamp, the mug, the books, the desk, the bookcase, the man in his wing chair and the plant. No third-party model is
shipped, so none needs an attribution.

Each file was made lighter for the web with `tools/desk-assets/optimize-props.sh`: textures reduced to 1024 px (2048 for
the man, whose face is seen close up) and converted to WebP, unused data removed, and for the pieces that are seen from
afar or in part (the desk, the bookcase, the books, the man, the plant) fewer triangles.

The desk is in the scene for its edge and its shadow: its top is covered by a photograph of walnut (`../desk-wood.webp`,
made from the user's own generated picture).
