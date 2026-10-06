"""Trains the evaluation network on the positions from shuffle.py.

    python train.py --name first --epochs 20

The target for a position is a blend of what Stockfish thought of it and how the game ended,
both turned into "chance of winning" in [0, 1] from the side to move's point of view:

    target = lambda * sigmoid(score / 400) + (1 - lambda) * result

and the network's output is judged by the squared error of sigmoid(output) against it. Each epoch
reports the error on the held-out positions; the best epoch is kept as runs/<name>/best.pt.
"""
from __future__ import annotations

import argparse
import csv
import time
from pathlib import Path

import numpy as np
import torch

from nnue import HIDDEN, SCALE, Net, decode
from records import RECORD

DATA = Path(__file__).parent / "data"
RUNS = Path(__file__).parent / "runs"


def load(path: Path, device: torch.device, gpu_budget_bytes: int) -> torch.Tensor:
    raw = torch.from_numpy(np.fromfile(path, dtype=np.uint8).reshape(-1, RECORD.itemsize))
    return raw.to(device) if raw.numel() <= gpu_budget_bytes else raw


def batches(data: torch.Tensor, size: int, device: torch.device, order: np.ndarray | None):
    count = (len(data) + size - 1) // size
    for i in (order if order is not None else range(count)):
        yield data[i * size:(i + 1) * size].to(device, non_blocking=True)


def loss_of(net: Net, raw: torch.Tensor, lam: float) -> torch.Tensor:
    white, black, stm, score, result = decode(raw)
    prediction = torch.sigmoid(net(white, black, stm))
    target = lam * torch.sigmoid(score / SCALE) + (1.0 - lam) * result
    return (prediction - target).square().mean()


@torch.no_grad()
def validate(net: Net, val: torch.Tensor, size: int, device: torch.device, lam: float) -> float:
    net.eval()
    total, n = 0.0, 0
    for raw in batches(val, size, device, None):
        total += loss_of(net, raw, lam).item() * len(raw)
        n += len(raw)
    net.train()
    return total / n


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--name", default="run")
    p.add_argument("--train", type=Path, default=DATA / "train.bin")
    p.add_argument("--val", type=Path, default=DATA / "val.bin")
    p.add_argument("--epochs", type=int, default=20)
    p.add_argument("--batch", type=int, default=16384)
    p.add_argument("--lr", type=float, default=1e-3)
    p.add_argument("--lr-final", type=float, default=1e-5, help="the learning rate falls to this by the last epoch")
    p.add_argument("--lam", type=float, default=0.75, help="weight of the engine score against the game result")
    p.add_argument("--hidden", type=int, default=HIDDEN)
    p.add_argument("--max-batches", type=int, default=0, help="stop each epoch early (for a quick test)")
    p.add_argument("--gpu-data-gb", type=float, default=2.5, help="keep the data on the GPU up to this size")
    p.add_argument("--resume", action="store_true", help="continue from runs/<name>/last.pt")
    args = p.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}" + (f" ({torch.cuda.get_device_name(0)})" if device.type == "cuda" else ""))
    budget = int(args.gpu_data_gb * 2 ** 30)
    train, val = load(args.train, device, budget), load(args.val, device, budget)
    print(f"{len(train):,} training positions, {len(val):,} validation positions")

    out_dir = RUNS / args.name
    out_dir.mkdir(parents=True, exist_ok=True)
    net = Net(args.hidden).to(device)
    optimizer = torch.optim.Adam(net.parameters(), lr=args.lr)
    # the rate decays smoothly (exponentially) from lr to lr-final
    decay = (args.lr_final / args.lr) ** (1.0 / max(1, args.epochs - 1))
    scheduler = torch.optim.lr_scheduler.ExponentialLR(optimizer, decay)
    start_epoch, best = 0, float("inf")
    if args.resume and (out_dir / "last.pt").exists():
        state = torch.load(out_dir / "last.pt", map_location=device)
        net.load_state_dict(state["net"])
        optimizer.load_state_dict(state["optimizer"])
        scheduler.load_state_dict(state["scheduler"])
        start_epoch, best = state["epoch"], state["best"]
        print(f"resumed after epoch {start_epoch}")

    log_path = out_dir / "log.csv"
    new_log = not log_path.exists() or not args.resume
    batch_count = (len(train) + args.batch - 1) // args.batch
    rng = np.random.default_rng(start_epoch + 1)

    with open(log_path, "w" if new_log else "a", newline="") as log_file:
        log = csv.writer(log_file)
        if new_log:
            log.writerow(["epoch", "train_loss", "val_loss", "lr", "seconds"])
        for epoch in range(start_epoch, args.epochs):
            started = time.time()
            order = rng.permutation(batch_count)
            if args.max_batches:
                order = order[:args.max_batches]
            running, seen = torch.zeros((), device=device), 0
            for raw in batches(train, args.batch, device, order):
                loss = loss_of(net, raw, args.lam)
                optimizer.zero_grad(set_to_none=True)
                loss.backward()
                optimizer.step()
                net.clip()
                running += loss.detach() * len(raw)
                seen += len(raw)
            scheduler.step()
            train_loss = (running / seen).item()
            val_loss = validate(net, val, args.batch, device, args.lam)
            seconds = time.time() - started
            marker = ""
            if val_loss < best:
                best, marker = val_loss, "  *best*"
                torch.save({"net": net.state_dict(), "hidden": args.hidden, "lam": args.lam, "epoch": epoch + 1}, out_dir / "best.pt")
            torch.save({"net": net.state_dict(), "optimizer": optimizer.state_dict(), "scheduler": scheduler.state_dict(),
                        "epoch": epoch + 1, "best": best, "hidden": args.hidden}, out_dir / "last.pt")
            log.writerow([epoch + 1, f"{train_loss:.6f}", f"{val_loss:.6f}", f"{scheduler.get_last_lr()[0]:.2e}", f"{seconds:.0f}"])
            log_file.flush()
            print(f"epoch {epoch + 1:>3}/{args.epochs}  train {train_loss:.5f}  val {val_loss:.5f}  "
                  f"{seen / seconds / 1e6:.2f}M pos/s  {seconds:.0f}s{marker}", flush=True)


if __name__ == "__main__":
    main()
