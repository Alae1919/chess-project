"""Downloads one monthly dump of the Lichess open database (CC0), resuming a partial download.

    python fetch.py 2017-01          # -> data/raw/lichess_db_standard_rated_2017-01.pgn.zst

The file is kept compressed; extract.py reads it as a stream, so it is never unpacked on disk.
See https://database.lichess.org for the months and their sizes.
"""
from __future__ import annotations

import argparse
import sys
import time
import urllib.request
from pathlib import Path

URL = "https://database.lichess.org/standard/lichess_db_standard_rated_{month}.pgn.zst"
RAW = Path(__file__).parent / "data" / "raw"


def fetch(month: str) -> Path:
    RAW.mkdir(parents=True, exist_ok=True)
    target = RAW / f"lichess_db_standard_rated_{month}.pgn.zst"
    url = URL.format(month=month)

    with urllib.request.urlopen(urllib.request.Request(url, method="HEAD")) as head:
        total = int(head.headers["Content-Length"])
    have = target.stat().st_size if target.exists() else 0
    if have == total:
        print(f"{target.name} is already complete ({total / 1e6:.0f} MB)")
        return target
    if have > total:
        target.unlink()
        have = 0

    request = urllib.request.Request(url, headers={"Range": f"bytes={have}-"} if have else {})
    print(f"downloading {url}: {have / 1e6:.0f} of {total / 1e6:.0f} MB")
    started, last = time.time(), 0.0
    with urllib.request.urlopen(request) as response, open(target, "ab" if have else "wb") as out:
        while chunk := response.read(1 << 20):
            out.write(chunk)
            have += len(chunk)
            if time.time() - last > 5:
                last = time.time()
                print(f"\r  {have / 1e6:7.0f} / {total / 1e6:.0f} MB  ({have / total:5.1%})", end="", flush=True)
    print(f"\ndone in {time.time() - started:.0f} s")
    if target.stat().st_size != total:
        sys.exit(f"incomplete download: {target.stat().st_size} of {total} bytes; run again to resume")
    return target


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("month", help="YYYY-MM")
    fetch(parser.parse_args().month)
