// src/app/shared/components/chess-board-3d/chess-board-3d.component.ts
import { AfterViewInit, Component, ElementRef, EventEmitter, inject, Input, Output, NgZone, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';
import { combineLatest, Subscription } from 'rxjs';
import {
  selectBoard,
  selectCurrentGame,
  selectCurrentTurn,
  selectLegalMoves,
  selectMovableColor,
  selectSelectedSquare,
} from '../../../store/game/game.selectors';
import { GameActions } from '../../../store/game/game.actions';
import { Piece, Square, Style3D } from '../../../core/models';
import { isPlayableStatus } from '../../../core/utils/game-status.utils';
import { initialSquares, LuxeBoardScene, Squares } from '../../three/luxe-board-scene';

/**
 * The REXCHESS Luxe 3D board.
 *
 * - `interactive`: bound to the game store (position, selection, legal moves, last move)
 *   and dispatches moves exactly like the 2D board. Drag to orbit.
 * - otherwise: a decorative starting position that follows the mouse.
 */
@Component({
  selector: 'app-chess-board-3d',
  standalone: true,
  template: `
    <div class="stage" [style.aspect-ratio]="aspect">
      <canvas #canvas></canvas>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .stage { position: relative; width: 100%; }
    .stage::before {
      content: ''; position: absolute; left: 5%; right: 5%; bottom: -4%; height: 14%;
      background: radial-gradient(ellipse, rgba(255, 170, 80, .18) 0%, transparent 70%);
      filter: blur(30px); pointer-events: none;
    }
    canvas { position: relative; display: block; width: 100%; height: 100%; touch-action: pan-y; }
  `],
})
export class ChessBoard3DComponent implements AfterViewInit, OnChanges, OnDestroy {
  /** When true, the board is shown from Black's point of view */
  @Input() flipped = false;
  /** Bind to the game store and accept moves; otherwise render a decorative start position */
  @Input() interactive = false;
  /** Board and piece style */
  @Input() look: Style3D = 'marble-gold';
  /** View the board straight from above instead of at an angle */
  @Input() topView = false;
  /** Emits when dragging the board ends up in (or out of) the top-down view */
  @Output() topViewChange = new EventEmitter<boolean>();
  /** Canvas width / height. Wider than 1 gives a tilted board more room to fill. */
  @Input() aspect = 1;

  @ViewChild('canvas', { static: true }) private canvasRef!: ElementRef<HTMLCanvasElement>;

  private store = inject(Store);
  private zone = inject(NgZone);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  private scene?: LuxeBoardScene;
  private sub = new Subscription();
  private resizeObserver?: ResizeObserver;
  private visibilityObserver?: IntersectionObserver;
  private lastBoard: Squares | null = null;
  /** Last value this board reported itself, so the page echoing it back doesn't snap the view */
  private reportedTopView?: boolean;
  private vm: any = null;

  ngAfterViewInit(): void {
    const canvas = this.canvasRef.nativeElement;

    this.zone.runOutsideAngular(() => {
      try {
        this.scene = new LuxeBoardScene(canvas, {
          interactive: this.interactive,
          look: this.look,
          onSquareClick: (sq) => this.zone.run(() => this.onSquareClick(sq)),
          onTopViewChange: (top) => this.zone.run(() => {
            this.reportedTopView = top;
            this.topViewChange.emit(top);
          }),
        });
      } catch (err) {
        console.error('[chess-board-3d] WebGL unavailable', err);
        return;
      }
      this.scene.setFlipped(this.flipped);
      this.scene.setTopView(this.topView, true);

      this.resizeObserver = new ResizeObserver(() => this.scene?.resize(canvas.clientWidth, canvas.clientHeight));
      this.resizeObserver.observe(canvas);
      // don't burn GPU on boards that are scrolled out of view
      this.visibilityObserver = new IntersectionObserver((entries) => this.scene?.setVisible(entries[entries.length - 1].isIntersecting));
      this.visibilityObserver.observe(canvas);
    });

    if (!this.scene) return;

    if (!this.interactive) {
      this.scene.setPosition(initialSquares(), false);
      return;
    }

    this.sub.add(
      combineLatest({
        board: this.store.select(selectBoard),
        selected: this.store.select(selectSelectedSquare),
        legalMoves: this.store.select(selectLegalMoves),
        currentTurn: this.store.select(selectCurrentTurn),
        // the side the viewer may move right now: not the opponent's, not the AI's
        movable: this.store.select(selectMovableColor),
        game: this.store.select(selectCurrentGame),
      }).subscribe((vm) => {
        this.vm = vm;
        this.zone.runOutsideAngular(() => this.sync());
      })
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['flipped']) this.scene?.setFlipped(this.flipped);
    if (changes['look']) this.scene?.setLook(this.look);
    if (changes['topView']) {
      // a drag that left the board part-way reports a side; the page echoing it back must not pull it to that end
      const echo = this.topView === this.reportedTopView;
      this.reportedTopView = undefined;
      if (!echo) this.scene?.setTopView(this.topView);
    }
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.resizeObserver?.disconnect();
    this.visibilityObserver?.disconnect();
    this.scene?.dispose();
  }

  private sync(): void {
    const scene = this.scene;
    const vm = this.vm;
    if (!scene || !vm) return;

    const squares: Squares | undefined = vm.board?.squares;
    if (squares && squares !== this.lastBoard) {
      this.lastBoard = squares;
      scene.setPosition(squares, true);
    }

    const moves = vm.game?.moves ?? [];
    const last = moves[moves.length - 1];
    scene.setHighlights({
      selected: vm.selected ?? null,
      hints: vm.legalMoves ?? [],
      lastMove: last ? [last.from, last.to] : null,
      check: this.findCheckedKing(vm),
    });
  }

  private findCheckedKing(vm: any): Square | null {
    if (!vm.board || (vm.game?.status !== 'check' && vm.game?.status !== 'checkmate')) return null;
    for (let row = 0; row < 8; row++) {
      for (let col = 0; col < 8; col++) {
        const p: Piece | null = vm.board.squares?.[row]?.[col];
        if (p?.type === 'king' && p.color === vm.currentTurn) return { row, col };
      }
    }
    return null;
  }

  /** Same move logic as the 2D board: select, move to a legal square, reselect or clear. */
  private onSquareClick(sq: Square): void {
    const vm = this.vm;
    if (!vm?.game || !isPlayableStatus(vm.game.status)) return;

    const piece: Piece | null = vm.board?.squares?.[sq.row]?.[sq.col] ?? null;

    if (vm.selected) {
      const isLegal = (vm.legalMoves ?? []).some((m: Square) => m.row === sq.row && m.col === sq.col);
      if (isLegal) {
        this.store.dispatch(
          GameActions.submitMove({
            move: {
              from: vm.selected,
              to: sq,
              piece: vm.board.squares[vm.selected.row][vm.selected.col],
              capturedPiece: piece ?? undefined,
            },
          })
        );
        return;
      }
      if (piece && piece.color === vm.movable) {
        this.store.dispatch(GameActions.selectSquare({ square: sq }));
        return;
      }
      this.store.dispatch(GameActions.clearSelection());
      return;
    }

    if (piece && piece.color === vm.movable) {
      this.store.dispatch(GameActions.selectSquare({ square: sq }));
    }
  }
}
