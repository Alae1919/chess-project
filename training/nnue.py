"""The network: 768 inputs -> 256 hidden (per perspective) -> SCReLU -> 1 output.

Inputs. A piece is one input, 12 piece kinds x 64 squares. Each side gets its own view of the
board: "own" pieces come first, the board is flipped vertically for Black, so the network
learns one set of weights for "my pieces" and "their pieces" whichever colour moves:

    index(perspective, pieceColor, type, square)
        = (pieceColor != perspective) * 384 + type * 64 + (perspective == white ? square : square ^ 56)

The same 768 x 256 weights serve both views. The two 256-wide results are concatenated with the
side to move first, so the output layer can treat "mine" and "theirs" differently:

    hidden = [ acc(stm), acc(other) ]          # 512
    out    = sum(screlu(hidden) * w_out) + b_out
    centipawns (side to move) = out * 400

SCReLU(x) = clamp(x, 0, 1)^2. Everything is quantised for the engine (see export.py):
the hidden layer by QA = 1024, the output layer by QB = 1024.
"""
from __future__ import annotations

import torch
from torch import nn

FEATURES = 768
HIDDEN = 256
QA = 1024
QB = 1024
SCALE = 400          # centipawns per unit of network output
WEIGHT_LIMIT = 1.98  # keeps the quantised weights, and the sums of them, inside 16 bits

# byte offsets inside a 32-byte record (see records.py)
OCC, PIECES, STM, RESULT, SCORE = 0, 8, 24, 25, 26


class Net(nn.Module):
    def __init__(self, hidden: int = HIDDEN):
        super().__init__()
        self.hidden = hidden
        # one row per input, so that switching a piece on adds one row: the same layout the engine uses
        self.ft_weight = nn.Parameter(torch.randn(FEATURES, hidden) * 0.05)
        self.ft_bias = nn.Parameter(torch.zeros(hidden))
        self.out = nn.Linear(2 * hidden, 1)
        nn.init.normal_(self.out.weight, 0.0, 0.05)
        nn.init.zeros_(self.out.bias)

    def forward(self, white_view: torch.Tensor, black_view: torch.Tensor, stm: torch.Tensor) -> torch.Tensor:
        """white_view, black_view: B x 768 one-hot inputs; stm: B (0 = White to move). Returns B outputs
        from the side to move's point of view."""
        w = white_view @ self.ft_weight + self.ft_bias
        b = black_view @ self.ft_weight + self.ft_bias
        white_to_move = (stm == 0).unsqueeze(1)
        hidden = torch.where(white_to_move, torch.cat([w, b], 1), torch.cat([b, w], 1))
        activated = hidden.clamp(0.0, 1.0).square()
        return self.out(activated).squeeze(1)

    @torch.no_grad()
    def clip(self) -> None:
        for p in (self.ft_weight, self.ft_bias, self.out.weight):
            p.clamp_(-WEIGHT_LIMIT, WEIGHT_LIMIT)

    def export_state(self) -> dict:
        """The parameters the engine file needs: ft_weight (FEATURES x hidden), ft_bias, out_weight, out_bias."""
        return {"ft_weight": self.ft_weight.detach().cpu(), "ft_bias": self.ft_bias.detach().cpu(),
                "out_weight": self.out.weight.detach().cpu(), "out_bias": self.out.bias.detach().cpu()}


def decode(raw: torch.Tensor):
    """From a B x 32 uint8 tensor of records (on any device) to what the network and the loss need:
    the two one-hot views, the side to move, the score for the side to move (centipawns) and the
    result for the side to move (0, 0.5, 1)."""
    device = raw.device
    n = raw.shape[0]
    occ = raw[:, OCC:OCC + 8].contiguous().view(torch.int64).squeeze(1)
    packed = raw[:, PIECES:PIECES + 16].to(torch.int64)
    nibbles = torch.stack([packed & 15, packed >> 4], 2).reshape(n, 32)
    stm = raw[:, STM].to(torch.int64)
    result_white = raw[:, RESULT].to(torch.float32) / 2.0
    score_white = raw[:, SCORE:SCORE + 2].contiguous().view(torch.int16).squeeze(1).to(torch.float32)

    squares = torch.arange(64, device=device, dtype=torch.int64)
    occupied = ((occ.unsqueeze(1) >> squares) & 1).bool()                     # B x 64
    rank = (torch.cumsum(occupied.to(torch.int64), 1) - 1).clamp(min=0)
    code = torch.gather(nibbles, 1, rank)                                       # B x 64, valid where occupied
    color, kind = code // 6, code % 6

    views = []
    for perspective in (0, 1):
        sq = squares.expand(n, 64) if perspective == 0 else (squares ^ 56).expand(n, 64)
        index = (color != perspective).to(torch.int64) * 384 + kind * 64 + sq
        # empty squares add 0 to input 0; each piece's input is distinct, so every sum is 0 or 1
        index = torch.where(occupied, index, torch.zeros_like(index))
        view = torch.zeros(n, FEATURES, device=device)
        view.scatter_add_(1, index, occupied.to(torch.float32))
        views.append(view)

    white_moves = stm == 0
    score = torch.where(white_moves, score_white, -score_white)
    result = torch.where(white_moves, result_white, 1.0 - result_white)
    return views[0], views[1], stm, score, result
