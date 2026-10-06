# Training the evaluation network

The engine's evaluation is a small neural network (768 inputs, 256 hidden units per side, one output)
trained on positions from games played on Lichess. Everything here is Python; the result is one small
file the Java engine loads.

```
python -m venv .venv && .venv\Scripts\activate
pip install -r requirements.txt
pip install torch --index-url https://download.pytorch.org/whl/cu126   # the GPU build

python fetch.py 2016-01                 # one monthly dump from database.lichess.org (CC0), kept compressed
python extract.py data/raw/lichess_db_standard_rated_2016-01.pgn.zst
python shuffle.py                       # merge, drop duplicates, shuffle, hold out 1% for validation
python train.py --name v1 --epochs 12   # runs/v1/best.pt
python export.py runs/v1/best.pt --out ../chess-engine/src/main/resources/nnue/default.nnue
python golden.py ../chess-engine/src/main/resources/nnue/default.nnue --val data/val.bin \
       --out ../chess-engine/src/test/resources/nnue/golden.csv
```

## Where the labels come from

Lichess marks the games a player asked the server to analyse with `[%eval ...]` comments: Stockfish's score
for the position after each move. About one game in seven has them. For every such game the pipeline keeps
the quiet positions (no check, the next move is not a capture or promotion, at least 10 half-moves in) with
that score and the game's result. The network learns a blend of the two:

    target = 0.75 * sigmoid(score / 400) + 0.25 * result          (all from the side to move's point of view)

The raw dump stays compressed (`extract.py` streams it), so a month needs only the size of its download.

## What is in the folder

| File | Role |
|---|---|
| `fetch.py`, `extract.py`, `shuffle.py` | the data pipeline; `records.py` is the 32-byte position record they share |
| `nnue.py`, `train.py` | the network and its training loop |
| `export.py` | quantises the weights to integers and writes the `.nnue` file the engine reads |
| `golden.py` | the engine's integer arithmetic, written separately in Python; its scores are what the Java tests must reproduce |
| `nnue_fixtures.py` | regenerates the small random network and `tiny-golden.csv` used by the Java tests |
| `match.py` | plays two UCI engines against each other and reports the Elo difference |
| `engine_fixtures.py` | reference data from python-chess for the engine's move generator and hashing tests |
| `test_pipeline.py`, `test_nnue.py` | `python -m unittest` |

## Notes

* Quantisation: hidden layer x1024, output layer x1024 (`nnue.py`). With 255 and 64 the rounding noise was about
  7 centipawns; with these it is under 1.
* The inputs are the pieces as seen by each side, board turned over for Black, so one set of weights serves both colours.
* Training speed on a GTX 1660 Ti: about 130k positions per second, 2.5 minutes per epoch on 19 million positions.
