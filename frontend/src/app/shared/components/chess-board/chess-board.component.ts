// src/app/shared/components/chess-board/chess-board.component.ts
import { Component, ElementRef, inject, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { combineLatest, tap } from 'rxjs';
import { AsyncPipe } from '@angular/common';
import {
  selectDisplayedBoard,
  selectSelectedSquare,
  selectLegalMoves,
  selectLegalMovesReady,
  selectCurrentTurn,
  selectCurrentGame,
  selectMovableColor,
  selectReviewPly,
  selectReviewedCheck,
  selectIsLoading,
  selectPendingPromotion,
  selectViewedMove,
} from '../../../store/game/game.selectors';
import { GameActions } from '../../../store/game/game.actions';
import { Move, Piece, PieceSet, PieceType, Square, Style2D } from '../../../core/models';
import { isPlayableStatus } from '../../../core/utils/game-status.utils';
import { DRAG_THRESHOLD_PX, dropVerdict, sameSquare } from '../../../core/utils/drag-drop.utils';
import { pieceImageUrl, pieceImageUrls } from '../../board/piece-sets';

// Solid glyphs for both colors (distinguished by CSS) — ︎ forces text, not emoji, rendering
const SOLID_PIECES: Record<PieceType, string> = {
  king: '♚︎', queen: '♛︎', rook: '♜︎', bishop: '♝︎', knight: '♞︎', pawn: '♟︎',
};

/** How long a piece takes to slide to its new square */
const MOVE_SLIDE_MS = 700;

/** A piece being carried: where it is on the board (px from the board's corner) and where it came from */
interface Carried {
  piece: Piece;
  from: Square;
  x: number;
  y: number;
  /** let go and heading for a square: the piece slides there instead of following the pointer */
  settling: boolean;
}

@Component({
  selector: 'app-chess-board',
  standalone: true,
  imports: [CommonModule, AsyncPipe],
  template: `
    <div class="board-wrap" [ngClass]="'board--' + boardStyle" [class.pieces-pending]="!imagesReady" *ngIf="vm$ | async as vm">
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
              [class.last-from]="isLastFrom(sq, vm.viewedMove)"
              [class.last-to]="isLastTo(sq, vm.viewedMove)"
              [class.in-check]="isKingInCheck(sq, vm)"
              [class.drop-target]="isDropTarget(sq, vm)"
              (click)="onSquareClick(sq, vm)"
              (pointerdown)="onPointerDown($event, sq, vm)"
            >
              <span class="sq-coord sq-coord--rank" *ngIf="i % 8 === 0" aria-hidden="true">{{ displayRanks[i / 8] }}</span>
              <span class="sq-coord sq-coord--file" *ngIf="i >= 56" aria-hidden="true">{{ displayFiles[i - 56] }}</span>
              <span *ngIf="getPiece(sq, vm.board) as piece" class="piece"
                    [class.piece--white]="piece.color === 'white'" [class.piece--black]="piece.color === 'black'"
                    [class.piece--img]="pieceSet !== 'glyph'" [style.background-image]="imageOf(piece)"
                    [class.piece--carried]="carried && isSelected(sq, carried.from)">
                {{ pieceSet === 'glyph' ? getPieceUnicode(piece) : '' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- File coordinates -->
      <div class="coord-files">
        <span *ngFor="let f of displayFiles">{{ f }}</span>
      </div>

      <!-- The piece in the player's hand -->
      <span *ngIf="carried as c" class="piece piece--ghost" aria-hidden="true"
            [class.piece--white]="c.piece.color === 'white'" [class.piece--black]="c.piece.color === 'black'"
            [class.piece--settling]="c.settling"
            [class.piece--img]="pieceSet !== 'glyph'" [style.background-image]="imageOf(c.piece)"
            [style.left.px]="c.x" [style.top.px]="c.y">{{ pieceSet === 'glyph' ? getPieceUnicode(c.piece) : '' }}</span>
    </div>
  `,
  styleUrls: ['./chess-board.component.scss'],
})
export class ChessBoardComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** When true, the board is shown from Black's point of view */
  @Input() flipped = false;
  /** Colour scheme of the flat board */
  @Input() boardStyle: Style2D = 'classic-wood';
  /** How the pieces are drawn: text symbols, or one of the image sets */
  @Input() set pieceSet(value: PieceSet | undefined) {
    const set = value ?? 'glyph';
    this._pieceSet = set;
    this.imagesReady = set === 'glyph' || typeof Image === 'undefined';
    if (!this.imagesReady) this.loadImages(set);
  }
  get pieceSet(): PieceSet { return this._pieceSet; }
  private _pieceSet: PieceSet = 'glyph';
  /** The pieces of an image set are drawn once all twelve are loaded, not one by one as they arrive */
  imagesReady = true;

  private loadImages(set: PieceSet): void {
    Promise.all(pieceImageUrls(set).map((url) => {
      const img = new Image();
      img.src = url;
      return img.decode().catch(() => undefined);   // a missing image must not keep the board empty
    })).then(() => { if (this._pieceSet === set) this.imagesReady = true; });
  }

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
    board: this.store.select(selectDisplayedBoard),
    selected: this.store.select(selectSelectedSquare),
    legalMoves: this.store.select(selectLegalMoves),
    legalMovesReady: this.store.select(selectLegalMovesReady),
    currentTurn: this.store.select(selectCurrentTurn),
    // the side the viewer may move right now: not the opponent's, not the AI's
    movable: this.store.select(selectMovableColor),
    game: this.store.select(selectCurrentGame),
    reviewing: this.store.select(selectReviewPly),
    reviewedCheck: this.store.select(selectReviewedCheck),
    loading: this.store.select(selectIsLoading),
    promoting: this.store.select(selectPendingPromotion),
    // the move that led to the board shown (the last one, unless looking back)
    viewedMove: this.store.select(selectViewedMove),
  }).pipe(tap((vm) => this.onStore(vm)));

  /** The piece being dragged, once the pointer has moved far enough to call it a drag */
  carried: Carried | null = null;
  /** The square under the pointer while dragging */
  private over: Square | null = null;

  private press: { from: Square; piece: Piece; x: number; y: number; id: number } | null = null;
  /** A piece let go before its legal moves arrived, or sent off and not yet answered */
  private dropped: { from: Square; to: Square; board: unknown; waiting: boolean } | null = null;
  private latest: any = null;
  private suppressClick = false;
  private unbind: Array<() => void> = [];

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.endListening();
  }

  onSquareClick(sq: Square, vm: any): void {
    // the click that ends a drag is not a tap
    if (this.suppressClick) return;
    if (!vm.game || !isPlayableStatus(vm.game.status)) return;

    const piece = this.getPiece(sq, vm.board);

    if (vm.selected) {
      const isLegal = (vm.legalMoves ?? []).some((m: Square) => m.row === sq.row && m.col === sq.col);
      if (isLegal) {
        this.submit(vm.selected, sq, vm);
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

  /* ── dragging ───────────────────────────────────────────────────────────── */

  onPointerDown(e: PointerEvent, sq: Square, vm: any): void {
    if (e.button !== 0) return;
    const piece = this.getPiece(sq, vm.board);
    if (!piece || piece.color !== vm.movable || !vm.game || !isPlayableStatus(vm.game.status)) return;

    // a new pick-up replaces whatever was left over from the last one
    this.endListening();
    this.carried = null;
    this.dropped = null;
    this.draggedMove = false;
    this.press = { from: sq, piece, x: e.clientX, y: e.clientY, id: e.pointerId };
    this.listen(window, 'pointermove', (ev: PointerEvent) => this.onPointerMove(ev));
    this.listen(window, 'pointerup', (ev: PointerEvent) => this.onPointerUp(ev));
    this.listen(window, 'pointercancel', () => this.cancelDrag());
  }

  private onPointerMove(e: PointerEvent): void {
    const press = this.press;
    if (!press || e.pointerId !== press.id) return;

    if (!this.carried) {
      if (Math.hypot(e.clientX - press.x, e.clientY - press.y) < DRAG_THRESHOLD_PX) return;
      // picked up: select it so its legal moves load and show
      if (!sameSquare(this.latest?.selected, press.from)) this.store.dispatch(GameActions.selectSquare({ square: press.from }));
      this.carried = { piece: press.piece, from: press.from, x: 0, y: 0, settling: false };
    }
    e.preventDefault();

    const wrap = this.wrapRect();
    const cell = this.cellSize();
    // under a finger the piece would be hidden: hold it a little above
    const lift = e.pointerType === 'touch' ? cell * 0.45 : 0;
    if (wrap) {
      this.carried.x = e.clientX - wrap.left;
      this.carried.y = e.clientY - wrap.top - lift;
    }
    this.over = this.squareAt(e.clientX, e.clientY);
  }

  private onPointerUp(e: PointerEvent): void {
    const press = this.press;
    if (!press || e.pointerId !== press.id) return;
    this.endListening();

    if (!this.carried) return;   // never moved: a tap, the click handler takes it from here

    // the click that follows a drag must not select or move anything
    this.suppressClick = true;
    setTimeout(() => (this.suppressClick = false), 0);

    const to = this.squareAt(e.clientX, e.clientY);
    this.over = null;
    if (!to || sameSquare(to, press.from)) {
      this.returnPiece();
      return;
    }

    const vm = this.latest;
    const verdict = dropVerdict(vm, press.from, to);
    if (verdict === 'reject') {
      this.returnPiece();
    } else if (verdict === 'accept') {
      this.sendOff(press.from, to, vm);
    } else {
      // its legal moves are still on the way: hold the piece over the square until they arrive
      this.dropped = { from: press.from, to, board: vm.board, waiting: true };
      this.slideTo(to);
    }
  }

  private cancelDrag(): void {
    this.endListening();
    this.over = null;
    if (this.carried) this.returnPiece();
  }

  /** Legal: play it, and keep the piece on its new square until the board catches up */
  private sendOff(from: Square, to: Square, vm: any): void {
    this.draggedMove = true;
    this.dropped = { from, to, board: vm.board, waiting: false };
    this.slideTo(to);
    this.submit(from, to, vm);
  }

  /** Not legal (or abandoned): the piece goes back where it came from and nothing is played */
  private returnPiece(): void {
    const c = this.carried;
    if (!c) return;
    this.dropped = null;
    this.draggedMove = false;
    this.slideTo(c.from);
    setTimeout(() => { if (this.carried === c) this.carried = null; }, 200);
  }

  private slideTo(sq: Square): void {
    const c = this.carried;
    const center = this.centerOf(sq);
    if (!c || !center) { this.carried = null; return; }
    c.settling = true;
    c.x = center.x;
    c.y = center.y;
  }

  /** Every store change: settle a piece that was let go, once the game has answered */
  private onStore(vm: any): void {
    this.latest = vm;
    this.watchForArrival(vm);
    const d = this.dropped;
    if (!d || !this.carried) return;

    if (vm.board !== d.board) {
      // the move went through: the real piece is on its square now
      this.dropped = null;
      this.carried = null;
    } else if (d.waiting) {
      if (!vm.selected) return this.returnPiece();   // the legal moves never came
      if (!vm.legalMovesReady) return;
      const verdict = dropVerdict(vm, d.from, d.to);
      if (verdict === 'accept') this.sendOff(d.from, d.to, vm);
      else this.returnPiece();
    } else if (!vm.loading && !vm.promoting) {
      // sent, answered, and the board did not change: the move was refused
      this.returnPiece();
    }
  }

  /* ── a move played by someone else (or by a tap) slides into place ──────── */

  /** The game and its move count at the last store change, to tell one new move from anything else */
  private shown: { id?: string; moves: number } | null = null;
  /** The move on its way was dragged by the player: the piece is already on its square, nothing to slide */
  private draggedMove = false;

  // One change of the store reaches the view model in several emissions (each selector in turn), so this
  // reads the move from the game itself rather than from the other parts of the model.
  private watchForArrival(vm: any): void {
    const was = this.shown;
    const game = vm.game;
    const moves: number = game?.moves?.length ?? 0;
    this.shown = { id: game?.id, moves };
    if (!was || !game || was.id !== game.id || moves !== was.moves + 1) return;
    const dragged = this.draggedMove;
    this.draggedMove = false;
    if (dragged || vm.reviewing !== null) return;
    this.slideIn(game.moves[moves - 1]);
  }

  /** The piece that just arrived travels from its old square to its new one (and the rook, when castling) */
  private slideIn(move: Move): void {
    const castles = move.piece?.type === 'king' && Math.abs(move.to.col - move.from.col) === 2;
    const rook = castles
      ? { from: { row: move.to.row, col: move.to.col > move.from.col ? 7 : 0 },
          to:   { row: move.to.row, col: move.to.col > move.from.col ? move.to.col - 1 : move.to.col + 1 } }
      : null;
    // wait for the board to be drawn with the piece on its new square
    setTimeout(() => {
      this.glide(move.from, move.to);
      if (rook) this.glide(rook.from, rook.to);
    }, 0);
  }

  private glide(from: Square, to: Square): void {
    const cell = this.cellSize();
    const el = this.pieceElAt(to);
    if (!el || !cell || typeof el.animate !== 'function') return;
    const col = (sq: Square) => (this.flipped ? 7 - sq.col : sq.col);
    const row = (sq: Square) => (this.flipped ? 7 - sq.row : sq.row);
    const dx = (col(from) - col(to)) * cell;
    const dy = (row(from) - row(to)) * cell;
    el.style.zIndex = '30';   // over the other pieces on the way
    const run = el.animate(
      [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'translate(0, 0)' }],
      { duration: MOVE_SLIDE_MS, easing: 'cubic-bezier(.45, .05, .25, 1)' },
    );
    run.onfinish = run.oncancel = () => { el.style.zIndex = ''; };
  }

  private pieceElAt(sq: Square): HTMLElement | null {
    const row = this.flipped ? 7 - sq.row : sq.row;
    const col = this.flipped ? 7 - sq.col : sq.col;
    const cells = this.host.nativeElement.querySelectorAll('.board-grid .sq');
    return cells[row * 8 + col]?.querySelector<HTMLElement>('.piece') ?? null;
  }

  private submit(from: Square, to: Square, vm: any): void {
    this.store.dispatch(
      GameActions.submitMove({
        move: {
          from,
          to,
          piece: this.getPiece(from, vm.board)!,
          capturedPiece: this.getPiece(to, vm.board) ?? undefined,
        },
      })
    );
  }

  private listen(target: EventTarget, type: string, fn: (e: any) => void): void {
    target.addEventListener(type, fn, { passive: false });
    this.unbind.push(() => target.removeEventListener(type, fn));
  }

  private endListening(): void {
    this.unbind.forEach((off) => off());
    this.unbind = [];
    this.press = null;
  }

  private wrapRect(): DOMRect | null {
    return this.host.nativeElement.querySelector('.board-wrap')?.getBoundingClientRect() ?? null;
  }

  private cellSize(): number {
    const grid = this.host.nativeElement.querySelector('.board-grid');
    return grid ? grid.getBoundingClientRect().width / 8 : 0;
  }

  /** Centre of a square, in px from the corner of the board wrap */
  private centerOf(sq: Square): { x: number; y: number } | null {
    const grid = this.host.nativeElement.querySelector('.board-grid')?.getBoundingClientRect();
    const wrap = this.wrapRect();
    if (!grid || !wrap) return null;
    const cell = grid.width / 8;
    const col = this.flipped ? 7 - sq.col : sq.col;
    const row = this.flipped ? 7 - sq.row : sq.row;
    return { x: grid.left - wrap.left + (col + 0.5) * cell, y: grid.top - wrap.top + (row + 0.5) * cell };
  }

  /** The board square under a point, or null off the board */
  private squareAt(x: number, y: number): Square | null {
    const grid = this.host.nativeElement.querySelector('.board-grid')?.getBoundingClientRect();
    if (!grid) return null;
    const cell = grid.width / 8;
    const col = Math.floor((x - grid.left) / cell);
    const row = Math.floor((y - grid.top) / cell);
    if (col < 0 || col > 7 || row < 0 || row > 7) return null;
    return this.displaySquares[row * 8 + col];
  }

  /** The square the piece in hand would land on, if that is a legal one */
  isDropTarget(sq: Square, vm: any): boolean {
    return !!this.carried && !this.carried.settling && sameSquare(this.over, sq) && this.isLegalMove(sq, vm.legalMoves);
  }

  /* ── squares and pieces ─────────────────────────────────────────────────── */

  isLightSquare(sq: Square): boolean {
    return (sq.row + sq.col) % 2 === 0;
  }

  isSelected(sq: Square, selected: Square | null): boolean {
    return !!selected && selected.row === sq.row && selected.col === sq.col;
  }

  /** The square the last move started from, and the one it ended on */
  isLastFrom(sq: Square, move: Move | null): boolean { return !!move && sameSquare(move.from, sq); }
  isLastTo(sq: Square, move: Move | null): boolean { return !!move && sameSquare(move.to, sq); }

  isLegalMove(sq: Square, legalMoves: Square[]): boolean {
    return (legalMoves ?? []).some((m) => m.row === sq.row && m.col === sq.col);
  }

  hasPiece(sq: Square, board: any): boolean {
    return !!board?.squares?.[sq.row]?.[sq.col];
  }

  getPiece(sq: Square, board: any): Piece | null {
    return board?.squares?.[sq.row]?.[sq.col] ?? null;
  }

  /** CSS background of a piece in an image set, none for the text symbols */
  imageOf(piece: Piece): string | null {
    return this._pieceSet === 'glyph' ? null : `url(${pieceImageUrl(this._pieceSet, piece)})`;
  }

  getPieceUnicode(piece: Piece): string {
    return SOLID_PIECES[piece.type];
  }

  isKingInCheck(sq: Square, vm: any): boolean {
    // on a past board, the move being looked at says whether it gave check
    if (vm.reviewing !== null) return sameSquare(vm.reviewedCheck, sq);
    const piece = this.getPiece(sq, vm.board);
    // Highlight king square when in check (backend signals this via game state)
    return (
      piece?.type === 'king' &&
      piece.color === vm.currentTurn &&
      vm.game?.status === 'check'
    );
  }
}
