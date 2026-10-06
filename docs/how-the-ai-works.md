# How the AI works

The AI opponent, the position evaluation bar and the hints all come from one chess engine written for this
project in Java, in `chess-engine/src/main/java/com/chess/engine/`. It does not depend on Spring, so it also runs
on its own as a UCI engine (`tools/engine/rexchess-uci.cmd`) and can be played in any chess GUI or tested against
other engines.

It has three parts: a fast board, a search that looks ahead, and an evaluation that says who is better in a
position. The evaluation is a small neural network trained on games played on Lichess.

```
 domain Board ──► Position (bitboards) ──► Searcher ──► best move
                                              │
                                              ▼
                                  Evaluator: neural network
                                  (classical evaluation for lone-king endings)
```

## The board

`Position` keeps the 12 piece sets as 64-bit integers (bitboards), and makes and unmakes moves in place, updating a
Zobrist hash incrementally. The hash follows the Polyglot standard, so one number serves the transposition table,
repetition detection and an opening book. Move generation is checked against two independent oracles:

* **perft**: the number of positions reachable in N moves from well-known test positions, which must match the
  published counts exactly;
* **python-chess**: 10,000 random positions, where the set of legal moves and the hash must equal python-chess's
  (`training/engine_fixtures.py` writes the reference files).

It searches about 600,000 positions per second on one core, with the old object-based board at roughly 20,000.

## The search

An alpha-beta search (principal variation search) with iterative deepening, searching one ply deeper each round
and using the last round to order moves. The usual techniques make it far more selective than brute force:
a transposition table (positions reached by different move orders are searched once), null-move pruning, late
move reductions, futility pruning, killer and history move ordering, static exchange evaluation for captures, and a
quiescence search that plays out captures before it trusts a score. Mate scores are never pruned or reduced, so
the shortest mate is found (a test checks hundreds of positions with a known mate in N against python-chess).

## The evaluation

### What it learns from

Lichess publishes every game played on the site under the CC0 licence (database.lichess.org). About one game in
seven was also analysed by Stockfish, and the analysis is stored in the game as `[%eval ...]` comments: a score for
the position after every move. The training data are those games.

| | |
|---|---|
| Source | `lichess_db_standard_rated_2016-01` |
| Games | 4,770,357, of which 702,897 analysed; 566,253 used (both players rated 1400 or more) |
| Positions | 20.6 million, 19.3 million after removing duplicates (19.15 million for training, 193,000 held out) |

A position is kept if at least ten half-moves have been played, the side to move is not in check, the next move is
not a capture or a promotion (so the score is of a quiet position, which a static evaluation can be expected to get
right) and Stockfish's score is within 30 pawns. Each position is stored with that score and with how the game ended.

### The network

768 inputs, one for each kind of piece on each square (12 x 64), as seen from the side to move and, separately, from
the other side (the board is turned over for Black, so one set of weights serves both colours). Each view goes through
the same 256 hidden units, the two results are joined (side to move first), squashed with clamp-and-square, and
reduced to one number: the evaluation in hundredths of a pawn.

It is trained to predict `0.75 x sigmoid(Stockfish score / 400) + 0.25 x game result`, from the side to move's point of
view, minimising the squared error. On held-out positions:

| Evaluation | Error vs. the training target | Error vs. Stockfish's score | Correlation with Stockfish |
|---|---|---|---|
| Classical, hand-written | 0.0231 | 0.0114 | 0.883 |
| Neural network | 0.0175 | 0.0068 | 0.927 |
| Always guessing "equal" | 0.0586 | | |

### Using it in the search

The first layer is the expensive one, and a move changes only two or three of the inputs. The engine keeps the hidden
sums for every position along the line being searched and updates them by adding and subtracting a row of weights
for each piece that moved, rather than computing them again. The weights are integers (so results are exactly
reproducible across machines): `training/golden.py` implements the same arithmetic separately in Python, and the Java
tests check that the engine returns exactly its score for 1,000 positions.

Speed: 530,000 positions per second with the network against 600,000 with the classical evaluation.

**Strength.** Over 200 games at 100 ms per move, the engine with the network beat the same engine with the classical
evaluation 122 to 43 with 35 draws: about **+145 Elo** (95% confidence: +98 to +192).

**Lone-king endings.** A network trained on game positions has hardly seen a bishop and a knight chasing a bare king,
and played them poorly (it mated with bishop and knight in 4 of 12 games, against 10 of 12 for the classical
evaluation). In those positions, and only those, the engine therefore uses the classical evaluation, which drives the
king to the edge. `training/endgames.py` is the test.

## Difficulty levels

Every level is the same engine; a level only decides how much it may think and how carefully it chooses among its
best moves. The first three levels search a few hundred to a few thousand positions, consider several candidate moves
and pick one at random with a probability that falls off with how much worse it is, so they make the kind of
inaccuracies a person does rather than the one-move blunders of a shallow search; the others always play the best
move they find, and differ in how long they look. In a game on a clock the AI also never
spends more than a share of the time left.

| Level | Name | Thinks | Strength* |
|---|---|---|---|
| 1 | Facile | 400 positions, picks among 5 moves | about 1100 |
| 2 | Moyen | 1,500 positions, among 4 moves | about 1400 |
| 3 | Difficile | 8,000 positions, among 3 moves | about 1750 |
| 4 | Expert | 5,000 positions, best move | about 2050 |
| 5 | Maître | 18,000 positions, best move | about 2550 |
| 6 | Maximum | 2 seconds, best move | about 2900 |

\* Measured by playing each level against Stockfish 19 held back to a chosen Elo (`UCI_LimitStrength`, 300 ms per
move), moving the opponent's Elo towards the level's own over three rounds of 16 games and fitting the rating that
best explains the results (`training/calibrate.py`; each estimate is good to about 100 Elo). It is on Stockfish's own scale, which is not the scale of Lichess
or of a chess club, and below 1,320 (the lowest Stockfish will play) it is an extrapolation. Treat the numbers as an
ordering with an order of magnitude attached.

## In the application

* **Playing the AI** (`AiPlayer`): converts the game to an engine position (with the earlier moves, so repetitions
  count), searches with the level's limits and returns a move. The search runs outside the game's lock, so resigning or
  undoing stops it at once.
* **Evaluation bar and hints** (`AnalysisService`, `GET /api/games/{id}/evaluation`): a 400 ms search of the current
  position, at most two at a time. Switched on with "Analyse" in the game, or "Analyse en temps réel" when creating the
  game. Not available during a live online game.
* **Configuration**: `app.engine.eval` is `nnue` (default) or `classical`; `app.engine.nnue-file` replaces the
  bundled network (`src/main/resources/nnue/default.nnue`, 386 KB) with another. If the network cannot be loaded the
  engine logs a warning and plays on with the classical evaluation.

## Reproducing and checking

```
# train (see training/README.md): about 40 minutes on a GTX 1660 Ti
python fetch.py 2016-01 && python extract.py data/raw/lichess_db_standard_rated_2016-01.pgn.zst
python shuffle.py && python train.py --name v1 --epochs 12
python export.py runs/v1/best.pt --out ../chess-engine/src/main/resources/nnue/default.nnue

# the engine on its own, speed and a signature of the search
tools\engine\rexchess-uci.cmd bench 8              # nodes searched is the signature: it changes with the search
# strength against another engine (a Stockfish binary is downloaded separately and not part of the project)
python match.py --a ..\tools\engine\rexchess-uci.cmd --a-opt Eval=nnue --b ..\tools\engine\rexchess-uci.cmd --b-opt Eval=classical
python calibrate.py --stockfish <path to stockfish>
```

Needs Java 17 (`JAVA_HOME`) and `mvn -f chess-engine/pom.xml compile` first.
