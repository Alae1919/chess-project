// src/app/features/landing/components/king-stage.component.ts
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, Input, NgZone, OnDestroy, ViewChild } from '@angular/core';
import { canUseWebGL } from '../webgl';
import type { KingStage } from '../scenes/king-stage';

/** Stills of the same scene, for phones, slow connections and browsers without WebGL */
const POSTERS = {
  close: 'assets/landing/king-close',
  wide: 'assets/landing/king-wide',
};

/**
 * The solitary king under its spotlight (acts I and V). Live Three.js where it is worth it,
 * otherwise two stills of the same scene, crossfaded and lit with CSS. Driven imperatively by the
 * page's scroll timeline through {@link set}, so scrolling never runs change detection.
 */
@Component({
  selector: 'app-king-stage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <img class="poster poster--close" #close alt="" decoding="async" fetchpriority="high"
         [src]="posters.close + '-1600.webp'"
         [attr.srcset]="posters.close + '-800.webp 800w, ' + posters.close + '-1600.webp 1600w'" sizes="100vw" />
    <img class="poster poster--wide" #wide alt="" decoding="async" loading="lazy"
         [src]="posters.wide + '-1600.webp'"
         [attr.srcset]="posters.wide + '-800.webp 800w, ' + posters.wide + '-1600.webp 1600w'" sizes="100vw" />
    <canvas #canvas aria-hidden="true"></canvas>
  `,
  styles: [`
    :host { position: absolute; inset: 0; display: block; background: #030201; overflow: hidden; }
    canvas, .poster { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
    .poster { object-fit: cover; opacity: 0; transition: none; }
    /* the close still is framed with the king right of centre; a narrow screen crops in on him */
    .poster--close { object-position: 68% 50%; }
    canvas { opacity: 0; transition: opacity .8s ease; }
    :host(.is-live) canvas { opacity: 1; }
  `],
})
export class KingStageComponent implements AfterViewInit, OnDestroy {
  /** Use the stills only (phones, data saver, reduced motion) */
  @Input() stillsOnly = false;
  /** Where the king stands across the screen, as a fraction from the middle (0.18 = right of centre) */
  @Input() focus = 0;

  @ViewChild('canvas', { static: true }) private canvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('close', { static: true }) private closeImg!: ElementRef<HTMLImageElement>;
  @ViewChild('wide', { static: true }) private wideImg!: ElementRef<HTMLImageElement>;

  readonly posters = POSTERS;
  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private zone = inject(NgZone);
  private stage?: KingStage;
  private resize?: ResizeObserver;
  private state = { light: 0, shot: 0, active: false };

  async ngAfterViewInit(): Promise<void> {
    this.paintStills();
    if (this.stillsOnly || !canUseWebGL()) return;

    // three.js and the scene load after the page is on screen; the stills cover the wait
    const { KingStage } = await import('../scenes/king-stage');
    this.zone.runOutsideAngular(() => {
      try {
        this.stage = new KingStage(this.canvas.nativeElement);
      } catch (err) {
        console.error('[king-stage] WebGL unavailable', err);
        return;
      }
      this.stage.setFocus(this.focusFor(this.state.shot));
      this.stage.setLight(this.state.light);
      this.stage.setShot(this.state.shot);
      this.stage.setActive(this.state.active);
      const el = this.canvas.nativeElement;
      this.resize = new ResizeObserver(() => this.stage?.resize(el.clientWidth, el.clientHeight));
      this.resize.observe(el);
      // swap the stills for the live scene once it has drawn its first frame
      requestAnimationFrame(() => this.host.nativeElement.classList.add('is-live'));
    });
  }

  /** light: 0 dark … 1 full spotlight; shot: 0 the close shot … 1 the wide one; active: draw frames */
  set(light: number, shot: number, active: boolean): void {
    const s = this.state;
    if (s.light === light && s.shot === shot && s.active === active) return;
    this.state = { light, shot, active };
    if (this.stage) {
      this.stage.setLight(light);
      this.stage.setShot(shot);
      this.stage.setFocus(this.focusFor(shot));
      this.stage.setActive(active);
    }
    this.paintStills();
  }

  /** Off-centre in the close shot, leaving room for the words; centred in the wide one, above them */
  private focusFor(shot: number): number {
    const k = Math.min(1, Math.max(0, (shot - 0.4) / 0.5));
    return this.focus * (1 - k * k * (3 - 2 * k));
  }

  /** The stills follow the same light and shot: brightness for the light, a crossfade for the shot */
  private paintStills(): void {
    if (this.stage && this.host.nativeElement.classList.contains('is-live')) return;
    const { light, shot } = this.state;
    const l = light * light * (3 - 2 * light);
    const k = Math.min(1, Math.max(0, (shot - 0.35) / 0.4));
    const filter = `brightness(${(0.08 + 0.92 * l).toFixed(3)})`;
    Object.assign(this.closeImg.nativeElement.style, { opacity: String(1 - k), filter, transform: `scale(${1.04 - 0.04 * l})` });
    Object.assign(this.wideImg.nativeElement.style, { opacity: String(k), filter });
  }

  ngOnDestroy(): void {
    this.resize?.disconnect();
    this.stage?.dispose();
  }
}
