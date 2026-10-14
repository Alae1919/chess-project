# Data provenance: landing prototype

Every engine number on the page is read from `data/showcase.json`, which `tools/record.py` writes from the real
REXCHESS engine over UCI. Nothing engine-related is typed into the page.

**Recording**
- Engine commit `acf5cf5`, with the `nnue` evaluation. Network `default.nnue`, SHA-256 `2e35c4e0…118cea`.
- Hash 16 MB. Depth-limited searches, each from a fresh engine state (`ucinewgame`).
- Reproducible: three runs gave identical node counts.

**Position**
- Source game: https://lichess.org/pT2w9HPz (popolesimi 2202 vs simbali 2164, rated classical game, 1 Jan 2016),
  from the Lichess open database (CC0).
- Move 26, White to play. White played Nf5.
- How it was chosen: by `find_candidates.py` (session scratchpad). The criteria were both players rated 1700+, past
  move 14, 20–35 legal moves, and a quiet knight move that Stockfish's stored evaluation shows losing 2+ pawns.
  Our engine then confirmed the refutation.
- Two other verified candidates: https://lichess.org/h1twyD9y (Nd5) and https://lichess.org/xQH40sVW (Nd3).

| Shown on the page | Value | Field |
|---|---|---|
| "25 legal moves" | 25 | `position.legalMoves` |
| "Each one searched to depth 13" | 13 | `meta.moveDepth` |
| Positions searched (nodes) | 12,586,660 | `totals.nodes`, the sum of `moves[].nodes`. The counter climbs through each move's own count |
| Best move | Kb1 | `moves[0].san` (ranked by `moves[].score`) |
| Evaluation, pawns, White's side | −0.43 | `moves[0].score.cp / 100` |
| The game move | Nf5, −9.07 | the `moves[]` entry where `san == source.played` |
| Refuted by | Qf4+ | that entry's `reply.san` (the first move of the search after Nf5) |
| Arcs in scene 3 | 25 moves | `moves[].from` / `.to`, drawn on `media/squares.json` (squares projected by the renderer) |
| Blue line after Kb1 | Rf4 Qc2 Qxc2+ | `root.pv[1..3]` (the root search, depth 14, which also chose Kb1) |
| Scene 4 moves | Nf5 Qf4+ Kb1 Qe4+ Kc1 Qxh1+ | `line[].san`. The best line after Nf5, from the depth-14 search |
| Scene 4 scores | −9.07 … −9.03 | `line[].score`: each position after the move, searched to depth 13 |
| Scene 4 captions | "Queen takes the rook on h1. Check." | built from `line[].piece`, `.captured`, `.to`, `.check` |
| Footer recording line | commit, network, depths, date | `meta.*` |

**Not engine data, and stated as such in the page footer**
- The opening film, the knight footage, and the king stills in scenes 5 and 6 are generated imagery.
- The board images in scenes 3 and 4 are renders of the real positions with Poly Haven's CC0 "Chess Set" (Riley
  Queen), made by `design/render/scene.html`.
