// src/app/features/landing/showcase.ts
// The engine's analysis behind the landing page (GET /api/showcase), and how it is written on screen.
// Every figure the page shows comes from here; when the analysis is not available the page shows none.
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, retry, shareReplay, startWith, throwError, timer } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface MoveRef { uci: string; san: string; }
/** From White's point of view: centipawns, or moves to mate (negative when Black mates) */
export interface Score { cp: number | null; mateIn: number | null; }
export interface Line { move: MoveRef; score: Score; pv: string[]; }
export interface Iteration { depth: number; nodes: number; timeMs: number; bestMove: MoveRef | null; score: Score; }

export interface Showcase {
  title: string;
  startFen: string;
  playerMove: MoveRef;
  fen: string;
  engine: {
    bestMove: MoveRef;
    score: Score;
    depth: number;
    nodes: number;
    timeMs: number;
    nodesPerSecond: number;
    lines: Line[];
    iterations: Iteration[];
  };
  replyFen: string;
  trap: { move: MoveRef; score: Score; refutation: string[]; depth: number; nodes: number };
  evaluator: string;
  analysedAt: string;
}

export type ShowcaseState =
  | { status: 'loading' }
  | { status: 'ready'; data: Showcase }
  | { status: 'unavailable' };

/**
 * The story the page tells, for the board and the captions while the analysis loads (or if it never
 * does). The same position as the server's ShowcaseService: White has just played 5.Nxe5.
 */
export const STORY = {
  fen: 'rn1qkbnr/ppp2p1p/3p2p1/4N3/2B1P1b1/2N5/PPPP1PPP/R1BQK2R b KQkq - 0 5',
  playerMove: { uci: 'f3e5', san: 'Nxe5' } as MoveRef,
};

@Injectable({ providedIn: 'root' })
export class ShowcaseService {
  private http = inject(HttpClient);

  /** Loading, then the analysis or nothing. A server still searching (503) is asked again a few times. */
  readonly state$: Observable<ShowcaseState> = this.http.get<Showcase>(`${environment.apiUrl}/showcase`).pipe(
    retry({
      count: 4,
      delay: (err: HttpErrorResponse) => (err.status === 503 ? timer(5000) : throwError(() => err)),
    }),
    map((data): ShowcaseState => (isShowcase(data) ? { status: 'ready', data } : { status: 'unavailable' })),
    catchError(() => of<ShowcaseState>({ status: 'unavailable' })),
    startWith<ShowcaseState>({ status: 'loading' }),
    shareReplay({ bufferSize: 1, refCount: false }),
  );
}

/** The fields the page relies on are all there (an old or broken server answers something else) */
export function isShowcase(d: unknown): d is Showcase {
  const s = d as Showcase;
  return !!s && typeof s.fen === 'string' && typeof s.replyFen === 'string' && !!s.playerMove?.uci
    && !!s.engine?.bestMove?.uci && Array.isArray(s.engine.lines) && Array.isArray(s.engine.iterations)
    && s.engine.iterations.length > 0 && !!s.trap?.move?.uci;
}

/* ── writing the figures ──────────────────────────────────────────────── */

const MINUS = '−';

/** +1.61, −0.40, 0.00, #2 (White mates in 2), −#3 (Black mates in 3) */
export function formatScore(s: Score | null | undefined): string {
  if (!s) return '';
  if (s.mateIn !== null && s.mateIn !== undefined) return (s.mateIn < 0 ? MINUS : '') + '#' + Math.abs(s.mateIn);
  const cp = s.cp ?? 0;
  if (cp === 0) return '0.00';
  return (cp > 0 ? '+' : MINUS) + (Math.abs(cp) / 100).toFixed(2);
}

/** 2,119,709 */
export function formatCount(n: number): string {
  return Math.round(n).toLocaleString('en-US');
}

/** 92 ms, 1.7 s, 6.0 s */
export function formatTime(ms: number): string {
  return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(1)} s`;
}

/** A line of moves in SAN, numbered from a FEN's move: "6. Qxg4 Nc6 7. d3" */
export function numberedLine(fen: string, sans: string[], max = sans.length): string {
  const [, side, , , , full] = fen.trim().split(/\s+/);
  let n = Number(full) || 1;
  let white = side !== 'b';
  const out: string[] = [];
  sans.slice(0, max).forEach((san, i) => {
    if (white) out.push(`${n}. ${san}`);
    else out.push(i === 0 ? `${n}… ${san}` : san);
    if (!white) n++;
    white = !white;
  });
  return out.join(' ');
}

/** The deepest iteration completed by `progress` (0..1) of the way through the search, in depth order */
export function iterationAt(its: Iteration[], progress: number): Iteration | null {
  if (!its.length) return null;
  const i = Math.min(its.length - 1, Math.max(0, Math.floor(progress * its.length)));
  return its[i];
}
