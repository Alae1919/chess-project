// src/app/shared/components/chess-board/chess-board.component.ts
import { Component, inject, OnInit, OnDestroy } from '@angular/core';
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
} from '../../../store/game/game.selectors';
import { GameActions } from '../../../store/game/game.actions';
import { Piece, PieceColor, PieceType, Square } from '../../../core/models';

const UNICODE_PIECES: Record<PieceColor, Record<PieceType, string>> = {
  white: { king: '♔', queen: '♕', rook: '♖', bishop: '♗', knight: '♘', pawn: '♙' },
  black: { king: '♚', queen: '♛', rook: '♜', bishop: '♝', knight: '♞', pawn: '♟' },
};

@Component({
  selector: 'app-chess-board',
  standalone: true,
  imports: [CommonModule, AsyncPipe],
  template: `
    <div class="board-wrap" *ngIf="vm$ | async as vm">
      <!-- Rank coordinates -->
      <div class="coord-ranks">
        <span *ngFor="let r of ranks">{{ r }}</span>
      </div>

      <div class="board-outer">
        <div class="board-frame">
          <div class="board-grid">
            <div
              *ngFor="let sq of allSquares"
              class="sq"
              [class.light]="isLightSquare(sq)"
              [class.dark]="!isLightSquare(sq)"
              [class.selected]="isSelected(sq, vm.selected)"
              [class.hint]="isLegalMove(sq, vm.legalMoves) && !hasPiece(sq, vm.board)"
              [class.capture]="isLegalMove(sq, vm.legalMoves) && hasPiece(sq, vm.board)"
              [class.in-check]="isKingInCheck(sq, vm)"
              (click)="onSquareClick(sq, vm)"
            >
              <span *ngIf="getPiece(sq, vm.board) as piece" class="piece">
                {{ getPieceUnicode(piece) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- File coordinates -->
      <div class="coord-files">
        <span *ngFor="let f of files">{{ f }}</span>
      </div>
    </div>
  `,
  styleUrls: ['./chess-board.component.scss'],
})
export class ChessBoardComponent implements OnInit {
  private store = inject(Store);

  readonly files = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
  readonly ranks = ['8', '7', '6', '5', '4', '3', '2', '1'];

  /** Flat array of all 64 squares in row-major order */
  readonly allSquares: Square[] = Array.from({ length: 64 }, (_, i) => ({
    row: Math.floor(i / 8),
    col: i % 8,
  }));

  vm$ = combineLatest({
    board: this.store.select(selectBoard),
    selected: this.store.select(selectSelectedSquare),
    legalMoves: this.store.select(selectLegalMoves),
    currentTurn: this.store.select(selectCurrentTurn),
    game: this.store.select(selectCurrentGame),
  });

  ngOnInit(): void {}

  onSquareClick(sq: Square, vm: any): void {
    if (!vm.game || vm.game.status !== 'active') return;

    const piece = this.getPiece(sq, vm.board);

    if (vm.selected) {
      const isLegal = vm.legalMoves.some((m: Square) => m.row === sq.row && m.col === sq.col);
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
      if (piece && piece.color === vm.currentTurn) {
        this.store.dispatch(GameActions.selectSquare({ square: sq }));
        return;
      }
      this.store.dispatch(GameActions.clearSelection());
      return;
    }

    // First click: select if own piece
    if (piece && piece.color === vm.currentTurn) {
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
    return legalMoves.some((m) => m.row === sq.row && m.col === sq.col);
  }

  hasPiece(sq: Square, board: any): boolean {
    return !!board?.squares?.[sq.row]?.[sq.col];
  }

  getPiece(sq: Square, board: any): Piece | null {
    return board?.squares?.[sq.row]?.[sq.col] ?? null;
  }

  getPieceUnicode(piece: Piece): string {
    return UNICODE_PIECES[piece.color][piece.type];
  }

  isKingInCheck(sq: Square, vm: any): boolean {
    const piece = this.getPiece(sq, vm.board);
    // Highlight king square when in check (backend signals this via game state)
    return (
      piece?.type === 'king' &&
      piece.color === vm.currentTurn &&
      vm.game?.status === 'active' // extend: add inCheck flag from backend
    );
  }
}
