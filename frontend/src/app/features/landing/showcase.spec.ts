import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, tick } from '@angular/core/testing';
import {
  formatCount, formatScore, formatTime, isShowcase, iterationAt, numberedLine,
  Showcase, ShowcaseService, ShowcaseState,
} from './showcase';

/** The shape the server sends (figures from a real depth-12 run, see ShowcaseService.java) */
export function sampleShowcase(): Showcase {
  const best = { uci: 'd6e5', san: 'dxe5' };
  return {
    title: "Légal's trap, Paris, about 1750",
    startFen: 'rn1qkbnr/ppp2p1p/3p2p1/4p3/2B1P1b1/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 5',
    playerMove: { uci: 'f3e5', san: 'Nxe5' },
    fen: 'rn1qkbnr/ppp2p1p/3p2p1/4N3/2B1P1b1/2N5/PPPP1PPP/R1BQK2R b KQkq - 0 5',
    engine: {
      bestMove: best, score: { cp: 161, mateIn: null }, depth: 12, nodes: 2119709, timeMs: 5995, nodesPerSecond: 353579,
      lines: [
        { move: best, score: { cp: 161, mateIn: null }, pv: ['dxe5', 'Qxg4', 'Nc6', 'd3'] },
        { move: { uci: 'g4e6', san: 'Be6' }, score: { cp: 161, mateIn: null }, pv: ['Be6', 'Bxe6'] },
      ],
      iterations: [
        { depth: 1, nodes: 457, timeMs: 92, bestMove: { uci: 'g4d1', san: 'Bxd1' }, score: { cp: 18, mateIn: null } },
        { depth: 2, nodes: 1199, timeMs: 140, bestMove: { uci: 'g4d1', san: 'Bxd1' }, score: { cp: 18, mateIn: null } },
        { depth: 3, nodes: 3556, timeMs: 167, bestMove: { uci: 'g4e6', san: 'Be6' }, score: { cp: 206, mateIn: null } },
        { depth: 4, nodes: 9112, timeMs: 203, bestMove: best, score: { cp: 172, mateIn: null } },
      ],
    },
    replyFen: 'rn1qkbnr/ppp2p1p/6p1/4p3/2B1P1b1/2N5/PPPP1PPP/R1BQK2R w KQkq - 0 6',
    trap: { move: { uci: 'g4d1', san: 'Bxd1' }, score: { cp: null, mateIn: 2 }, refutation: ['Bxf7+', 'Ke7', 'Nd5#'], depth: 3, nodes: 326 },
    evaluator: 'neural network evaluation (256 hidden units)',
    analysedAt: '2026-10-13T12:00:00Z',
  };
}

describe('showcase formatting', () => {
  it('writes scores from White’s side, with a real minus sign and mates as #n', () => {
    expect(formatScore({ cp: 161, mateIn: null })).toBe('+1.61');
    expect(formatScore({ cp: -40, mateIn: null })).toBe('−0.40');
    expect(formatScore({ cp: 0, mateIn: null })).toBe('0.00');
    expect(formatScore({ cp: null, mateIn: 2 })).toBe('#2');
    expect(formatScore({ cp: null, mateIn: -3 })).toBe('−#3');
    expect(formatScore(null)).toBe('');
  });

  it('groups counts and writes times', () => {
    expect(formatCount(2119709)).toBe('2,119,709');
    expect(formatTime(92)).toBe('92 ms');
    expect(formatTime(5995)).toBe('6.0 s');
  });

  it('numbers a line from the side to move', () => {
    const black = 'x/x/x/x/x/x/x/x b - - 0 5';
    expect(numberedLine(black, ['dxe5', 'Qxg4', 'Nc6', 'd3'])).toBe('5… dxe5 6. Qxg4 Nc6 7. d3');
    expect(numberedLine('x w - - 0 6', ['Bxf7+', 'Ke7', 'Nd5#'])).toBe('6. Bxf7+ Ke7 7. Nd5#');
    expect(numberedLine(black, ['dxe5', 'Qxg4', 'Nc6'], 2)).toBe('5… dxe5 6. Qxg4');
  });

  it('steps through the iterations as the scroll goes, never between them', () => {
    const its = sampleShowcase().engine.iterations;
    expect(iterationAt(its, 0)?.depth).toBe(1);
    expect(iterationAt(its, 0.3)?.depth).toBe(2);
    expect(iterationAt(its, 0.99)?.depth).toBe(4);
    expect(iterationAt(its, 1)?.depth).toBe(4);
    expect(iterationAt([], 0.5)).toBeNull();
  });

  it('accepts only a complete analysis', () => {
    expect(isShowcase(sampleShowcase())).toBeTrue();
    expect(isShowcase({})).toBeFalse();
    expect(isShowcase({ ...sampleShowcase(), engine: { ...sampleShowcase().engine, iterations: [] } })).toBeFalse();
  });
});

describe('ShowcaseService', () => {
  let http: HttpTestingController;
  let states: ShowcaseState[];

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    states = [];
  });

  afterEach(() => http.verify());

  it('is loading, then ready with the server’s analysis', () => {
    TestBed.inject(ShowcaseService).state$.subscribe((s) => states.push(s));
    http.expectOne('/api/showcase').flush(sampleShowcase());
    expect(states.map((s) => s.status)).toEqual(['loading', 'ready']);
  });

  it('asks again while the server is still searching', fakeAsync(() => {
    TestBed.inject(ShowcaseService).state$.subscribe((s) => states.push(s));
    http.expectOne('/api/showcase').flush({ message: 'thinking' }, { status: 503, statusText: 'Unavailable' });
    tick(5000);
    http.expectOne('/api/showcase').flush(sampleShowcase());
    expect(states[states.length - 1].status).toBe('ready');
  }));

  it('is unavailable, with no figures, when the server fails', () => {
    TestBed.inject(ShowcaseService).state$.subscribe((s) => states.push(s));
    http.expectOne('/api/showcase').flush({}, { status: 500, statusText: 'Error' });
    expect(states[states.length - 1]).toEqual({ status: 'unavailable' });
  });
});
