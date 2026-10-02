import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PieceType } from '../models';
import { GameService } from './game.service';

describe('GameService.submitMove', () => {
  let service: GameService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GameService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** The UCI string sent for a move from e7 (row 1) to e8 (row 0). */
  function sentMove(promotion?: PieceType): string {
    service.submitMove('game-1', {
      from: { row: 1, col: 4 }, to: { row: 0, col: 4 },
      piece: { type: 'pawn', color: 'white' }, promotion,
    }).subscribe({ error: () => {} });
    const req = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/games/game-1/moves'));
    const body = req.request.body.move;
    req.flush(null);
    return body;
  }

  it('sends plain moves as from+to', () => {
    expect(sentMove()).toBe('e7e8');
  });

  it('appends the promotion letter, n for a knight', () => {
    expect(sentMove('queen')).toBe('e7e8q');
    expect(sentMove('knight')).toBe('e7e8n');
  });
});
