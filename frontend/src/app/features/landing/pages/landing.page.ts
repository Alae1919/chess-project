// src/app/features/landing/pages/landing.page.ts
import {
  AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, ElementRef, inject, NgZone,
  OnDestroy, QueryList, ViewChild, ViewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { moveLabel } from '../../../core/utils/fen.utils';
import { KingStageComponent } from '../components/king-stage.component';
import { SearchStageComponent } from '../components/search-stage.component';
import { DuelBoardComponent } from '../components/duel-board.component';
import { ShotComponent } from '../components/shot.component';
import { Shot, SHOTS } from '../landing-media';
import {
  formatCount, formatScore, formatTime, Iteration, iterationAt, Line, numberedLine, Score, Showcase,
  ShowcaseService, STORY,
} from '../showcase';

gsap.registerPlugin(ScrollTrigger);

/** The acts, where each starts on the film's 0–100 timeline, and where it is fully on screen */
export const ACTS = [
  { numeral: 'I', title: 'The silence before the move', start: 0, rest: 9 },
  { numeral: 'II', title: 'The human instinct', start: 16, rest: 23 },
  { numeral: 'III', title: 'Inside the machine', start: 32, rest: 47 },
  { numeral: 'IV', title: 'The duel', start: 58, rest: 74 },
  { numeral: 'V', title: 'Your move', start: 82, rest: 99 },
] as const;

/** The act a point of the timeline belongs to */
export function actAt(t: number): number {
  let i = 0;
  ACTS.forEach((a, k) => { if (t >= a.start) i = k; });
  return i;
}

/** Reduced motion: the moment shown for a scroll position — the opening title first, then each act at rest */
export function stillTime(t: number): number {
  return t < ACTS[0].rest - 4 ? 0 : ACTS[actAt(t)].rest;
}

/** When each shot is on screen, on the film's timeline, for the clips that play */
const SHOT_TIMES: Record<Shot['id'], [number, number]> = { hand: [14, 31], knight: [25, 37], kings: [79, 100] };

/** Values the timeline animates, read on every update and handed to the scenes */
interface Rig {
  light: number;     // the king's spotlight
  push: number;      // the camera's push toward the king
  search: number;    // through the search clip: the tree grows, then narrows to one square
  ladder: number;    // through the search's depths
  converge: number;  // the weighed replies narrow to one (the list)
  appear: number;    // the arcs light up on the board
  fan: number;       // … and narrow to the one played
  tilt: number;      // top view → the game's view
  reply: number;     // the engine's move, played past 0.5
  settle: number;    // its arc fades once it has landed
}

/**
 * The landing page: one scroll-driven film in five acts. A tall track holds a sticky stage; a GSAP
 * timeline scrubbed by the scroll crossfades the footage, moves the 3D scenes and reveals the text.
 * The numbers on screen come from the engine's real analysis (GET /api/showcase), or are not shown.
 */
@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [RouterLink, KingStageComponent, SearchStageComponent, DuelBoardComponent, ShotComponent],
  templateUrl: './landing.page.html',
  styleUrls: ['./landing.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPage implements AfterViewInit, OnDestroy {
  @ViewChild('film', { static: true }) private film!: ElementRef<HTMLElement>;
  @ViewChild('stage', { static: true }) private stage!: ElementRef<HTMLElement>;
  @ViewChild(KingStageComponent, { static: true }) private king!: KingStageComponent;
  @ViewChild(SearchStageComponent, { static: true }) private searchStage!: SearchStageComponent;
  @ViewChild(DuelBoardComponent, { static: true }) private duel!: DuelBoardComponent;
  @ViewChildren(ShotComponent) private shotViews!: QueryList<ShotComponent>;

  readonly acts = ACTS;
  readonly shots = SHOTS;
  readonly story = STORY;

  /** prefers-reduced-motion: the acts change in place, nothing moves */
  readonly still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  /** Phones and data savers get the stills instead of live 3D for the king, and no video */
  readonly lite = matchMedia('(max-width: 768px)').matches
    || !!(navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData;
  readonly wide = matchMedia('(min-width: 1025px)').matches;
  readonly narrow = matchMedia('(max-width: 768px)').matches;

  loading = true;
  data: Showcase | null = null;
  /** The search at the depth the scroll has reached */
  iteration: Iteration | null = null;
  /** The deepest iteration whose choice was to take the queen (0 if none) */
  trapDepth = 0;
  converged = false;
  replied = false;
  act = 0;

  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private zone = inject(NgZone);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);
  private ctx?: gsap.Context;
  private trigger?: ScrollTrigger;
  private tl?: gsap.core.Timeline;
  private scroller?: HTMLElement;
  private rig: Rig = { light: 0.45, push: 0, search: 0, ladder: 0, converge: 0, appear: 0, fan: 0, tilt: 0, reply: 0, settle: 0 };
  private intro = { v: 0 };
  private viewReady = false;

  constructor() {
    inject(ShowcaseService).state$.pipe(takeUntilDestroyed()).subscribe((s) => {
      this.loading = s.status === 'loading';
      this.data = s.status === 'ready' ? s.data : null;
      this.trapDepth = this.data
        ? Math.max(0, ...this.data.engine.iterations.filter((i) => i.bestMove?.uci === this.data!.trap.move.uci).map((i) => i.depth))
        : 0;
      if (this.viewReady) {
        this.duel.setAnalysis(this.data);
        this.apply(true);
      } else {
        this.cdr.markForCheck();
      }
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.duel.setAnalysis(this.data);
    this.scroller = this.host.nativeElement.closest<HTMLElement>('.app-main') ?? undefined;
    this.zone.runOutsideAngular(() => this.build());
  }

  ngOnDestroy(): void {
    this.trigger?.kill();
    this.ctx?.revert();
  }

  /* ── what the template writes ─────────────────────────────────────────── */

  score = (s: Score | null | undefined): string => formatScore(s);
  count = (n: number | undefined): string => (n === undefined ? '' : formatCount(n));
  time = (ms: number): string => formatTime(ms);

  /** "5. Nxe5" */
  get playerMoveLabel(): string {
    const d = this.data;
    return d ? moveLabel(d.startFen, d.playerMove.san) : `5. ${STORY.playerMove.san}`;
  }

  /** "5… dxe5" */
  replyLabel(d: Showcase, san: string): string {
    return moveLabel(d.fen, san);
  }

  /** The replies the engine weighed, and the trap if it is not among them */
  weighed(d: Showcase): Array<Line & { trap: boolean; best: boolean }> {
    const lines = d.engine.lines.map((l) => ({ ...l, trap: l.move.uci === d.trap.move.uci, best: l.move.uci === d.engine.bestMove.uci }));
    if (!lines.some((l) => l.trap)) lines.push({ move: d.trap.move, score: d.trap.score, pv: [], trap: true, best: false });
    return lines;
  }

  /** The mate after taking the queen: "6. Bxf7+ Ke7 7. Nd5#" */
  refutation(d: Showcase): string {
    const full = Number(d.fen.trim().split(/\s+/)[5]) || 1;
    return numberedLine(`x w - - 0 ${full + 1}`, d.trap.refutation);
  }

  /** The line the engine expects after its reply, in full moves */
  expected(d: Showcase): string {
    return numberedLine(d.fen, d.engine.lines[0]?.pv ?? [d.engine.bestMove.san], 7);
  }

  evaluatorName(d: Showcase): string {
    return d.evaluator.replace(/\s*\(.*\)$/, '').replace(/:.*$/, '');
  }

  /** Scroll to an act (the chapter buttons) */
  goTo(i: number): void {
    const st = this.trigger;
    if (!st) return;
    const top = st.start + (st.end - st.start) * (ACTS[i].rest / 100);
    (this.scroller ?? window).scrollTo({ top, behavior: this.still ? 'auto' : 'smooth' });
  }

  /* ── the film ─────────────────────────────────────────────────────────── */

  private build(): void {
    const stage = this.stage.nativeElement;
    this.ctx = gsap.context(() => {
      const tl = (this.tl = gsap.timeline({ paused: true, defaults: { ease: 'none' }, onUpdate: () => this.apply() }));
      const r = this.rig;
      const fadeIn = (sel: string, at: number, dur = 3, y = 26): void => {
        tl.fromTo(sel, { autoAlpha: 0, y }, { autoAlpha: 1, y: 0, duration: dur, ease: 'power2.out' }, at);
      };
      const fadeOut = (sel: string, at: number, dur = 2.5, y = -18): void => {
        tl.to(sel, { autoAlpha: 0, y, duration: dur, ease: 'power2.in' }, at);
      };
      const cross = (layer: string, at: number, dur = 3.5): void => {
        tl.to(`[data-layer=${layer}]`, { autoAlpha: 1, duration: dur, ease: 'power1.inOut' }, at);
      };
      const out = (layer: string, at: number, dur = 3): void => {
        tl.to(`[data-layer=${layer}]`, { autoAlpha: 0, duration: dur, ease: 'power1.inOut' }, at);
      };
      const drift = (layer: string, at: number, from: number, to: number, dur: number): void => {
        tl.fromTo(`[data-layer=${layer}] app-shot`, { scale: from }, { scale: to, duration: dur }, at);
      };

      // I — the silence before the move: the light comes up, the camera moves in on the king
      tl.to('.cue', { autoAlpha: 0, duration: 1.5 }, 0);
      tl.to('[data-copy=hero]', { autoAlpha: 0, y: -30, duration: 4.5, ease: 'power1.in' }, 0.5);
      tl.to(r, { light: 1, duration: 11, ease: 'power1.inOut' }, 0);
      tl.to(r, { push: 1, duration: 17, ease: 'power1.inOut' }, 0.5);
      tl.fromTo('[data-layer=king]', { scale: 1 }, { scale: 1.06, duration: 17 }, 0);
      fadeIn('[data-copy=act1]', 5);
      fadeOut('[data-copy=act1]', 13);

      // II — the human instinct: the hand, the knight
      cross('hand', 15);
      drift('hand', 15, 1.0, 1.08, 15);
      out('king', 15.5, 3.5);
      fadeIn('[data-copy=act2]', 18.5);
      fadeOut('[data-copy=act2]', 29);
      cross('knight', 25.5, 4);
      drift('knight', 25.5, 1.03, 1.1, 12);
      out('hand', 27.5);

      // III — inside the machine: the search tree grows with the depth, then narrows to one square
      cross('search', 32, 4);
      out('knight', 34);
      fadeIn('[data-copy=act3]', 33);
      fadeIn('[data-copy=ladder]', 36);
      tl.to(r, { ladder: 1, duration: 14 }, 37);
      // the clip: its tree grows over the depths, is fully drawn as the replies appear, and narrows with them
      tl.to(r, { search: 0.5, duration: 15 }, 36);
      tl.to(r, { search: 1, duration: 7 }, 51.5);
      fadeOut('[data-copy=act3]', 50.5);
      fadeOut('[data-copy=ladder]', 51);
      fadeIn('[data-copy=lines]', 51.5);
      tl.to(r, { converge: 1, duration: 4 }, 55);

      // IV — the duel: the footage gives way to the real board, which turns, and the engine plays
      cross('board', 57, 3.5);
      // the real board starts at the size of the board in the last frame of footage, then settles
      tl.fromTo('[data-layer=board]', { scale: this.wide ? 1.2 : this.narrow ? 1.9 : 1.45 }, { scale: 1, duration: 4.5, ease: 'power2.out' }, 57);
      tl.to(r, { appear: 1, duration: 3 }, 57);
      out('search', 59);
      fadeOut('[data-copy=lines]', 60);
      tl.to(r, { fan: 1, duration: 3.5, ease: 'power1.inOut' }, 61.5);
      tl.to(r, { tilt: 1, duration: 9, ease: 'power1.inOut' }, 61);
      // wide screens: the board makes room on its left for the words
      if (this.wide) tl.fromTo('[data-layer=board]', { xPercent: 0 }, { xPercent: 14, duration: 6, ease: 'power1.inOut' }, 59);
      fadeIn('[data-copy=act4]', 66);
      tl.to(r, { reply: 1, duration: 0.6 }, 67);
      tl.to(r, { settle: 1, duration: 3 }, 68.8);
      fadeIn('[data-copy=reply]', 68);
      fadeOut('[data-copy=act4]', 79);
      fadeOut('[data-copy=reply]', 79);
      out('board', 80, 4);

      // V — your move: the two kings face each other across the board, and the way in
      cross('kings', 80.5, 4.5);
      drift('kings', 80.5, 1.1, 1.0, 18.5);
      fadeIn('[data-copy=act5]', 86, 3.5);
      tl.fromTo('.final__mark', { autoAlpha: 0, y: 14 }, { autoAlpha: 1, y: 0, duration: 3, ease: 'power2.out' }, 89);
      tl.fromTo('.final__cta', { autoAlpha: 0, y: 14 }, { autoAlpha: 1, y: 0, duration: 3, ease: 'power2.out' }, 91);
      tl.to({}, { duration: 1 }, 99);

      // seeking doesn't fire the timeline's updates: hand the values over here
      const showStill = (progress: number): void => {
        tl.seek(stillTime(progress * 100));
        this.apply();
      };
      this.trigger = ScrollTrigger.create({
        trigger: this.film.nativeElement,
        scroller: this.scroller,
        start: 'top top',
        end: 'bottom bottom',
        // reduced motion: each act is shown at rest, switched in place rather than animated
        ...(this.still
          ? { onUpdate: (self: ScrollTrigger) => showStill(self.progress) }
          : { animation: tl, scrub: 0.7 }),
        onRefresh: (self: ScrollTrigger) => { if (this.still) showStill(self.progress); },
      });
      if (this.still) showStill(0);

      // the light finds the king as the page opens (not tied to the scroll)
      if (this.still) this.intro.v = 1;
      else gsap.to(this.intro, { v: 1, duration: 2.6, delay: 0.3, ease: 'power2.inOut', onUpdate: () => this.apply() });
      this.apply(true);
    }, stage);
  }

  /** Hand the timeline's values to the scenes and to the text that depends on them */
  private apply(force = false): void {
    const r = this.rig;
    // where the film is (it eases after the scroll), and where the scroll is (for the chapters)
    const t = (this.trigger?.progress ?? 0) * 100;
    const time = this.tl?.time() ?? 0;

    this.king?.set(r.light * this.intro.v, r.push, time < 21);
    this.searchStage?.set(r.search, time > 30 && time < 60);
    this.shotViews?.forEach((v) => {
      const at = SHOT_TIMES[v.shot.id];
      v.setPlaying(time >= at[0] && time <= at[1]);
    });
    if (this.duel) {
      this.duel.setActive(time > 55 && time < 85);
      this.duel.setTilt(r.tilt);
      this.duel.setCalculation(r.appear, r.fan, r.settle);
      this.duel.setReplied(r.reply > 0.5);
    }

    // the text that changes with the scroll: re-rendered only when it does change
    const it = this.data ? iterationAt(this.data.engine.iterations, r.ladder) : null;
    const converged = r.converge > 0.5;
    const replied = r.reply > 0.5;
    const act = actAt(t);
    if (force || it !== this.iteration || converged !== this.converged || replied !== this.replied || act !== this.act) {
      this.iteration = it;
      this.converged = converged;
      this.replied = replied;
      this.act = act;
      this.cdr.detectChanges();
    }
  }
}
