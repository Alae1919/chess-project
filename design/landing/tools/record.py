"""Record the landing page's engine data from the real REXCHESS engine (UCI), at fixed depths.

Every search starts from a fresh engine state (ucinewgame) and is limited by depth only, so the same engine
commit and network give the same numbers on every run. Nothing here is hand-written: the page shows only
fields from the JSON this writes.

usage (from the repo root, Java 17 and a compiled engine):
  training/.venv/Scripts/python.exe -I design/landing/tools/record.py design/landing/data/showcase.json
"""
import datetime
import hashlib
import json
import subprocess
import sys

import chess
import chess.engine

OUT = sys.argv[1]
JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-17.0.20.1+1\bin\java.exe"
ENGINE = [JAVA, "-Xss4m", "-cp", "chess-engine/target/classes", "com.chess.engine.uci.UciMain"]
NETWORK = "chess-engine/src/main/resources/nnue/default.nnue"
ROOT_DEPTH, MOVE_DEPTH, LINE_PLIES = 14, 13, 6

# Lichess game https://lichess.org/pT2w9HPz (Lichess open database, CC0), White to move at move 26.
SOURCE = {"site": "https://lichess.org/pT2w9HPz", "event": "Rated Classical game", "date": "2016.01.01",
          "white": "popolesimi", "whiteElo": 2202, "black": "simbali", "blackElo": 2164,
          "licence": "Lichess open database, CC0", "played": "Nf5"}
FEN = "5r1k/p1p3b1/1p5p/3Pp1p1/6P1/1QP3N1/PP3q1P/2K4R w - - 3 26"


def white_cp(score):
    """{'cp': n} or {'mate': n}, from White's point of view."""
    s = score.white()
    return {"mate": s.mate()} if s.is_mate() else {"cp": s.score()}


def search(eng, board, depth):
    info = eng.analyse(board, chess.engine.Limit(depth=depth), game=object())   # a new game: fresh tables
    return info


def describe(board, move):
    victim = board.piece_type_at(chess.square(chess.square_file(move.to_square), chess.square_rank(move.from_square))
                                 if board.is_en_passant(move) else move.to_square)
    return {"uci": move.uci(), "san": board.san(move), "from": chess.square_name(move.from_square),
            "to": chess.square_name(move.to_square), "piece": chess.piece_name(board.piece_type_at(move.from_square)),
            "captured": chess.piece_name(victim) if board.is_capture(move) and victim else None,
            "capture": board.is_capture(move), "check": board.gives_check(move)}


def main():
    board = chess.Board(FEN)
    eng = chess.engine.SimpleEngine.popen_uci(ENGINE)
    try:
        root = search(eng, board, ROOT_DEPTH)
        pv_board = board.copy()
        root_pv = []
        for m in root["pv"]:
            root_pv.append(describe(pv_board, m))
            pv_board.push(m)

        moves = []
        for move in board.legal_moves:
            entry = describe(board, move)
            board.push(move)
            info = search(eng, board, MOVE_DEPTH)
            reply = info["pv"][0] if info.get("pv") else None
            entry.update({"score": white_cp(info["score"]), "depth": info.get("depth"), "nodes": info.get("nodes"),
                          "reply": describe(board, reply) if reply else None})
            board.pop()
            moves.append(entry)
        moves.sort(key=lambda e: e["score"].get("cp", 100000 * (1 if e["score"].get("mate", 0) > 0 else -1)), reverse=True)
        for i, e in enumerate(moves):
            e["rank"] = i + 1

        # the consequence: the engine's best line after the move the human played, each position evaluated
        played = chess.Move.from_uci(next(e["uci"] for e in moves if e["san"] == SOURCE["played"]))
        line_board = board.copy()
        first = describe(line_board, played)
        line_board.push(played)
        after = search(eng, line_board, ROOT_DEPTH)
        plies = [played] + list(after["pv"])[: LINE_PLIES - 1]
        line, b = [], board.copy()
        for m in plies:
            step = describe(b, m)
            b.push(m)
            ev = search(eng, b, MOVE_DEPTH)
            step.update({"fen": b.fen(), "score": white_cp(ev["score"]), "depth": ev.get("depth")})
            line.append(step)
        line_meta = {"from": first["san"], "searchDepth": ROOT_DEPTH, "searchNodes": after.get("nodes"),
                     "score": white_cp(after["score"])}
    finally:
        eng.quit()

    commit = subprocess.run(["git", "log", "-1", "--format=%h", "--", "chess-engine/src/main/java/com/chess/engine",
                             "chess-engine/src/main/resources/nnue"], capture_output=True, text=True).stdout.strip()
    data = {
        "meta": {
            "engine": "REXCHESS", "engineCommit": commit, "eval": "nnue",
            "network": {"file": NETWORK, "sha256": hashlib.sha256(open(NETWORK, "rb").read()).hexdigest()},
            "hashMb": 16, "rootDepth": ROOT_DEPTH, "moveDepth": MOVE_DEPTH,
            "generated": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
            "recorder": "design/landing/tools/record.py, python-chess " + chess.__version__ + " over UCI",
            "method": "depth-limited searches, each from a fresh engine state (ucinewgame)",
        },
        "source": SOURCE,
        "position": {"fen": FEN, "sideToMove": "white", "fullmove": 26, "legalMoves": len(moves)},
        "root": {"depth": root.get("depth"), "nodes": root.get("nodes"), "score": white_cp(root["score"]),
                 "best": describe(board, root["pv"][0]), "pv": root_pv},
        "moves": moves,
        "totals": {"movesSearched": len(moves), "nodes": sum(e["nodes"] or 0 for e in moves)},
        "line": line, "lineMeta": line_meta,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
    best = moves[0]
    print(f"root depth {data['root']['depth']} nodes {data['root']['nodes']} best {data['root']['best']['san']} {data['root']['score']}")
    print(f"per-move best {best['san']} {best['score']} | played {SOURCE['played']} rank "
          f"{next(e['rank'] for e in moves if e['san'] == SOURCE['played'])}/{len(moves)}")
    print("line:", " ".join(s["san"] for s in line), "|", " ".join(str(s["score"]) for s in line))
    print("total nodes", data["totals"]["nodes"])


if __name__ == "__main__":
    main()
