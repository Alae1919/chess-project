// src/app/features/landing/components/duel-board.component.ts
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, inject, Input, NgZone, OnDestroy, ViewChild } from '@angular/core';
import { MiniBoardComponent } from '../../../shared/components/mini-board/mini-board.component';
import { Piece, Square } from '../../../core/models';
import { fenToSquares, uciSquares } from '../../../core/utils/fen.utils';
import type { Arc, LuxeBoardScene } from '../../../shared/three/luxe-board-scene';
import { Showcase, STORY } from '../showcase';
import { canUseWebGL } from '../webgl';

/** The colour of the engine's thinking, and only of that */
const ENGINE_BLUE = 0x4da6ff;

/**
 * Act IV: the game's own 3D board (the Ebony & Ivory style), showing the story's position, the
 * replies the engine weighed as arcs of blue light, and then the reply it chose, played with a blue
 * light travelling with the piece. Everything it draws comes from the analysis it is given.
 * Without WebGL it is the flat thumbnail board, showing the same positions.
 */
@Component({
  selector: 'app-duel-board',
  standalone: true,
  imports: [MiniBoardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <canvas #canvas aria-hidden="true"></canvas>
    @if (flat) {
      <div class="flat"><app-mini-board [squares]="flatSquares" [size]="flatSize" [label]="label" /></div>
    }
  `,
  styles: [`
    :host { position: absolute; inset: 0; display: block; }
    canvas { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
    .flat { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; }
  `],
})
export class DuelBoardComponent implements AfterViewInit, OnDestroy {
  /** Play moves without moving pieces (reduced motion) */
  @Input() still = false;

  @ViewChild('canvas', { static: true }) private canvas!: ElementRef<HTMLCanvasElement>;

  flat = false;
  flatSquares: (Piece | null)[][] = fenToSquares(STORY.fen);
  flatSize = 320;
  label = 'The position after 5. Nxe5';

  private zone = inject(NgZone);
  private cdr = inject(ChangeDetectorRef);
  private scene?: LuxeBoardScene;
  private resize?: ResizeObserver;
  private data: Showcase | null = null;
  private replied = false;
  private tilt = 0;
  private arcs: Arc[] = [];
  private active = false;

  async ngAfterViewInit(): Promise<void> {
    if (!canUseWebGL()) return this.useFlat();
    const { LuxeBoardScene } = await import('../../../shared/three/luxe-board-scene');
    this.zone.runOutsideAngular(() => {
      try {
        this.scene = new LuxeBoardScene(this.canvas.nativeElement, {
          interactive: false,
          look: 'ebony-ivory',
          moveMs: 1300,
          hop: 0.32,
          moveLight: ENGINE_BLUE,
          // phones: the camera comes closer, so the board fills the width
          tight: matchMedia('(max-width: 768px)').matches,
        });
      } catch (err) {
        console.error('[duel-board] WebGL unavailable', err);
        this.zone.run(() => this.useFlat());
        return;
      }
      const el = this.canvas.nativeElement;
      this.resize = new ResizeObserver(() => this.scene?.resize(el.clientWidth, el.clientHeight));
      this.resize.observe(el);
      this.scene.setVisible(this.active);
      this.scene.setTopAmount(1 - this.tilt, true);
      this.sync(false);
    });
  }

  /** The analysis arrived (or not: the board then shows the story's position, and no reply) */
  setAnalysis(data: Showcase | null): void {
    this.data = data;
    this.sync(false);
  }

  /** 0 = straight above, as the last footage frame; 1 = the game's tilted view */
  setTilt(t: number): void {
    this.tilt = t;
    this.scene?.setTopAmount(1 - t * 0.9, true);
  }

  /**
   * The calculation, as arcs. `appear` brings in every reply the engine weighed (the trap included);
   * `converge` takes away all but the one it chose; `settle` takes that one away too, once played.
   */
  setCalculation(appear: number, converge: number, settle: number): void {
    const d = this.data;
    if (!d) return;
    const best = d.engine.bestMove.uci;
    const ucis = [...d.engine.lines.map((l) => l.move.uci), d.trap.move.uci].filter((u, i, all) => all.indexOf(u) === i);
    this.arcs = ucis.map((uci) => {
      const { from, to } = uciSquares(uci);
      const strength = uci === best ? appear * (1 - settle) : appear * (1 - converge) * 0.7;
      return { from, to, strength };
    });
    this.scene?.setArcs(this.arcs, ENGINE_BLUE);
  }

  /** Before or after the engine's reply */
  setReplied(replied: boolean): void {
    if (replied === this.replied) return;
    this.replied = replied;
    this.sync(!this.still);
  }

  setActive(active: boolean): void {
    if (active === this.active) return;
    this.active = active;
    this.scene?.setVisible(active);
  }

  ngOnDestroy(): void {
    this.resize?.disconnect();
    this.scene?.dispose();
  }

  /** Put the right position on the board, with the last move marked */
  private sync(animate: boolean): void {
    const d = this.data;
    const showReply = this.replied && !!d;
    const fen = showReply ? d!.replyFen : d?.fen ?? STORY.fen;
    const last = showReply ? d!.engine.bestMove.uci : (d?.playerMove ?? STORY.playerMove).uci;
    const squares = fenToSquares(fen);
    const { from, to } = uciSquares(last);
    if (this.scene) {
      this.scene.setPosition(squares, animate);
      this.scene.setHighlights({ selected: null, hints: [], lastMove: [from, to] as [Square, Square], check: null });
    }
    if (this.flat) {
      this.flatSquares = squares;
      this.label = showReply ? `The position after ${d!.engine.bestMove.san}` : 'The position after 5. Nxe5';
      this.cdr.markForCheck();
    }
  }

  private useFlat(): void {
    this.flat = true;
    this.flatSize = Math.round(Math.min(window.innerWidth * 0.82, window.innerHeight * 0.62, 520));
    this.sync(false);
    this.cdr.markForCheck();
  }
}
