# Piece artwork

The pieces of the flat board. The sets the app ships are in `frontend/src/assets/pieces/` (one folder per set,
twelve files each: `w` or `b` for the colour, then `K` `Q` `R` `B` `N` `P`, so `wK.svg` is the white king and
`bN.svg` the black knight). This folder keeps the sources and what was not added to the app.

## In the app

| Folder in `assets/pieces/` | Name in the app | What it is |
|---|---|---|
| `classic/` | Classique | The traditional drawing. From the `pieces-svg` set by Uray M. János (2013–2018), after Colin M. L. Burnett's Wikipedia pieces. The file headers say Creative Commons attribution, share-alike: keep them. |
| `contour/` | Contour | Black silhouettes, white pieces drawn as outlines on a white fill. Cut from a traced sheet of twelve pieces (`sheets/contour-set-26774.svg` here). Where it comes from is not known: check its licence before the site goes public. |

"Symboles" in the picker is not a folder: it draws the text characters, and always works. Only the flat board uses
these sets; the 3D board has its own modelled pieces.

## Not in the app

| Here | What | Why |
|---|---|---|
| `maestro/` | The "maestro" set by sadsnake (shaded pieces). | **The black pawn is missing**: the original `bP.svg` is an empty drawing (no shapes), so only eleven files are here. Add a real `bP.svg`, then add the set as below. Check its licence first: the files do not state one. |
| `variants/` | Pieces for chess variants (amazon, archbishop, centaur, chancellor, grasshopper...), both colours. | Not usable in a standard game. Same artist as `classic`, same licence. |
| `sheets/silhouettes-6-2490568.svg` | Traced sheet of six black silhouettes: king, queen, knight, pawn, rook and a helmeted piece. | One colour only, and the sixth piece does not clearly read as a bishop. |

Both sheets were made with potrace: shapes only, no author or licence.

## Adding a set

1. Make a folder in `frontend/src/assets/pieces/` with the twelve files. Centre each piece in a square view box,
   standing on the same line, and fill it (a white piece that is only an outline shows the square through it).
2. Add the folder name to the `PieceSet` type in `frontend/src/app/core/models/index.ts`.
3. Add one line to `PIECE_SETS` in `frontend/src/app/shared/board/piece-sets.ts`.

The picker, the saved choice and the board take it from there.
