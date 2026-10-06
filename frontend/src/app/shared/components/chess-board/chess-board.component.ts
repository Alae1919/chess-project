// src/app/shared/components/chess-board/chess-board.component.ts
import { Component, inject, Input, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { combineLatest, map } from 'rxjs';
import { AsyncPipe } from '@angular/common';
import {
  selectBoard,
  selectSelectedSquare,
  selectLegalMoves,
  selectCurrentTurn,
  selectCurrentGame,
  selectMovableColor,
} from '../../../store/game/game.selectors';
import { GameActions } from '../../../store/game/game.actions';
import { Piece, PieceColor, PieceType, Square, Style2D } from '../../../core/models';
import { isPlayableStatus } from '../../../core/utils/game-status.utils';

// Solid glyphs for both colors (distinguished by CSS) — ︎ forces text, not emoji, rendering
const SOLID_PIECES: Record<PieceType, string> = {
  king: '♚︎', queen: '♛︎', rook: '♜︎', bishop: '♝︎', knight: '♞︎', pawn: '♟︎',
};

@Component({
  selector: 'app-chess-board',
  standalone: true,
  imports: [CommonModule, AsyncPipe],
  template: `
    <div class="board-wrap" [ngClass]="'board--' + boardStyle" *ngIf="vm$ | async as vm">
      <!-- Rank coordinates -->
      <div class="coord-ranks">
        <span *ngFor="let r of displayRanks">{{ r }}</span>
      </div>

      <div class="board-outer">
        <div class="board-frame">
          <div class="board-grid">
            <div
              *ngFor="let sq of displaySquares; let i = index"
              class="sq"
              [class.light]="isLightSquare(sq)"
              [class.dark]="!isLightSquare(sq)"
              [class.selected]="isSelected(sq, vm.selected)"
              [class.hint]="isLegalMove(sq, vm.legalMoves) && !hasPiece(sq, vm.board)"
              [class.capture]="isLegalMove(sq, vm.legalMoves) && hasPiece(sq, vm.board)"
              [class.in-check]="isKingInCheck(sq, vm)"
              (click)="onSquareClick(sq, vm)"
            >
              <span class="sq-coord sq-coord--rank" *ngIf="i % 8 === 0" aria-hidden="true">{{ displayRanks[i / 8] }}</span>
              <span class="sq-coord sq-coord--file" *ngIf="i >= 56" aria-hidden="true">{{ displayFiles[i - 56] }}</span>
              <span *ngIf="getPiece(sq, vm.board) as piece" class="piece" [class.piece--white]="piece.color === 'white'" [class.piece--black]="piece.color === 'black'">
                {{ getPieceUnicode(piece) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- File coordinates -->
      <div class="coord-files">
        <span *ngFor="let f of displayFiles">{{ f }}</span>
      </div>
    </div>
  `,
  styleUrls: ['./chess-board.component.scss'],
})
export class ChessBoardComponent implements OnInit {
  private store = inject(Store);

  /** When true, the board is shown from Black's point of view */
  @Input() flipped = false;
  /** Colour scheme of the flat board */
  @Input() boardStyle: Style2D = 'classic-wood';

  readonly files = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
  readonly ranks = ['8', '7', '6', '5', '4', '3', '2', '1'];

  /** Flat array of all 64 squares in row-major order */
  readonly allSquares: Square[] = Array.from({ length: 64 }, (_, i) => ({
    row: Math.floor(i / 8),
    col: i % 8,
  }));

  get displaySquares(): Square[] { return this.flipped ? [...this.allSquares].reverse() : this.allSquares; }
  get displayRanks(): string[] { return this.flipped ? [...this.ranks].reverse() : this.ranks; }
  get displayFiles(): string[] { return this.flipped ? [...this.files].reverse() : this.files; }

  vm$ = combineLatest({
    board: this.store.select(selectBoard),
    selected: this.store.select(selectSelectedSquare),
    legalMoves: this.store.select(selectLegalMoves),
    currentTurn: this.store.select(selectCurrentTurn),
    // the side the viewer may move right now: not the opponent's, not the AI's
    movable: this.store.select(selectMovableColor),
    game: this.store.select(selectCurrentGame),
  });

  ngOnInit(): void {}

  onSquareClick(sq: Square, vm: any): void {
    if (!vm.game || !isPlayableStatus(vm.game.status)) return;

    const piece = this.getPiece(sq, vm.board);

    if (vm.selected) {
      const isLegal = (vm.legalMoves ?? []).some((m: Square) => m.row === sq.row && m.col === sq.col);
      if (isLegal) {
        // Dispatch move
        this.store.dispatch(
          GameActions.submitMove({
            move: {
              from: vm.selected,
              to: sq,
              piece: this.getPiece(vm.selected, vm.board)!,
              capturedPiece: piece ?? undefined,
            },
          })
        );
        return;
      }
      // Clicked on another own piece → reselect
      if (piece && piece.color === vm.movable) {
        this.store.dispatch(GameActions.selectSquare({ square: sq }));
        return;
      }
      this.store.dispatch(GameActions.clearSelection());
      return;
    }

    // First click: select if own piece
    if (piece && piece.color === vm.movable) {
      this.store.dispatch(GameActions.selectSquare({ square: sq }));
    }
  }

  isLightSquare(sq: Square): boolean {
    return (sq.row + sq.col) % 2 === 0;
  }

  isSelected(sq: Square, selected: Square | null): boolean {
    return !!selected && selected.row === sq.row && selected.col === sq.col;
  }

  isLegalMove(sq: Square, legalMoves: Square[]): boolean {
    return (legalMoves ?? []).some((m) => m.row === sq.row && m.col === sq.col);
  }

  hasPiece(sq: Square, board: any): boolean {
    return !!board?.squares?.[sq.row]?.[sq.col];
  }

  getPiece(sq: Square, board: any): Piece | null {
    return board?.squares?.[sq.row]?.[sq.col] ?? null;
  }

  getPieceUnicode(piece: Piece): string {
    return SOLID_PIECES[piece.type];
  }

  isKingInCheck(sq: Square, vm: any): boolean {
    const piece = this.getPiece(sq, vm.board);
    // Highlight king square when in check (backend signals this via game state)
    return (
      piece?.type === 'king' &&
      piece.color === vm.currentTurn &&
      vm.game?.status === 'check'
    );
  }
}
