// src/app/core/services/game.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { UCI_PROMOTION_LETTER } from '../utils/promotion.utils';
import {
  BoardState,
  Game,
  GameOptions,
  GamePlayer,
  GameStatus,
  Move,
  PieceType,
  Piece,
  PieceColor,
  SavedGame,
  PositionEvaluation,
  Square,
} from '../models';

@Injectable({ providedIn: 'root' })
export class GameService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/games`;

  // ─── API response transformation ─────────────────────────────────────────

  private parseFen(fen: string): BoardState {
    const FEN_MAP: Record<string, Piece> = {
      r: { type: 'rook',   color: 'black' }, n: { type: 'knight', color: 'black' },
      b: { type: 'bishop', color: 'black' }, q: { type: 'queen',  color: 'black' },
      k: { type: 'king',   color: 'black' }, p: { type: 'pawn',   color: 'black' },
      R: { type: 'rook',   color: 'white' }, N: { type: 'knight', color: 'white' },
      B: { type: 'bishop', color: 'white' }, Q: { type: 'queen',  color: 'white' },
      K: { type: 'king',   color: 'white' }, P: { type: 'pawn',   color: 'white' },
    };
    const squares = fen.split(' ')[0].split('/').map(row => {
      const cols: (Piece | null)[] = [];
      for (const ch of row) {
        if (/\d/.test(ch)) cols.push(...Array(+ch).fill(null));
        else cols.push(FEN_MAP[ch] ?? null);
      }
      return cols;
    });
    return { squares };
  }

  private mapStatus(s: string): GameStatus {
    const statusMap: Record<string, GameStatus> = {
      ONGOING: 'active',          CHECK: 'check',
      CHECKMATE: 'checkmate',     STALEMATE: 'stalemate',
      DRAW_50_MOVE: 'draw_50_move',
      DRAW_INSUFFICIENT_MATERIAL: 'draw_insufficient_material',
      DRAW_REPETITION: 'draw_repetition',
      WHITE_RESIGNED: 'white_resigned',
      BLACK_RESIGNED: 'black_resigned',
      DRAW_AGREED: 'draw_agreed',
      WAITING: 'waiting',  PAUSED: 'paused',
      FINISHED: 'finished', ABORTED: 'aborted',
    };
    return statusMap[s] ?? (s as GameStatus);
  }

  private defaultPlayer(color: PieceColor): GamePlayer {
    return { username: color === 'white' ? 'White' : 'Black', color, timeRemainingMs: 0, capturedPieces: [] };
  }

  /** Backend only sends UCI strings (moveHistory); build minimal Move objects for the notation panel. */
  private movesFromHistory(history?: string[]): Move[] {
    return (history ?? []).map(uci => ({
      from: { col: uci.charCodeAt(0) - 97, row: 8 - Number(uci[1]) },
      to:   { col: uci.charCodeAt(2) - 97, row: 8 - Number(uci[3]) },
      promotion: uci.length > 4 ? ({ q: 'queen', r: 'rook', b: 'bishop', n: 'knight' } as Record<string, PieceType>)[uci[4].toLowerCase()] : undefined,
      algebraicNotation: uci.length > 4 ? `${uci.slice(0, 2)}-${uci.slice(2, 4)}=${uci[4].toUpperCase()}` : `${uci.slice(0, 2)}-${uci.slice(2)}`,
    }) as Move);
  }

  mapGame(raw: any): Game {
    return {
      ...raw,
      board:       raw.board       ?? (raw.fen ? this.parseFen(raw.fen) : null),
      status:      this.mapStatus(raw.status ?? ''),
      playerWhite: raw.playerWhite ?? this.defaultPlayer('white'),
      playerBlack: raw.playerBlack ?? this.defaultPlayer('black'),
      moves:       raw.moves?.length ? raw.moves : this.movesFromHistory(raw.moveHistory),
    };
  }

  // ─── API methods ─────────────────────────────────────────────────────────

  /** Create a new game with chosen options */
  createGame(options: GameOptions): Observable<Game> {
    return this.http.post<Game>(this.base, options).pipe(map((raw: any) => this.mapGame(raw)));
  }

  /** Get a game by ID */
  getGame(gameId: string): Observable<Game> {
    return this.http.get<Game>(`${this.base}/${gameId}`).pipe(map((raw: any) => this.mapGame(raw)));
  }

  /**
   * Submit a move to the Java backend.
   * The backend expects UCI format: { move: "d2d4" }.
   */
  submitMove(gameId: string, move: Omit<Move, 'algebraicNotation' | 'timestamp'>): Observable<Game> {
    const uci = this.squareToAlg(move.from) + this.squareToAlg(move.to)
      + (move.promotion ? UCI_PROMOTION_LETTER[move.promotion] ?? '' : '');
    return this.http.post<Game>(`${this.base}/${gameId}/moves`, { move: uci })
      .pipe(map((raw: any) => this.mapGame(raw)));
  }

  private squareToAlg(sq: Square): string {
    return String.fromCharCode(97 + sq.col) + (8 - sq.row);
  }

  /** Ask the AI to play its move; backend applies it and returns the updated game */
  getAiMove(gameId: string): Observable<Game> {
    return this.http.post<Game>(`${this.base}/${gameId}/ai-move`, {}).pipe(map((raw: any) => this.mapGame(raw)));
  }

  /** Undo the last move (if allowed by game options) */
  undoMove(gameId: string): Observable<Game> {
    return this.http.delete<Game>(`${this.base}/${gameId}/moves/last`).pipe(map((raw: any) => this.mapGame(raw)));
  }

  /** Save current game state */
  saveGame(gameId: string): Observable<SavedGame> {
    return this.http.post<SavedGame>(`${this.base}/${gameId}/save`, {});
  }

  /** Load all saved games for the current user (backend: GET /api/users/me/saved-games) */
  getSavedGames(): Observable<SavedGame[]> {
    return this.http.get<SavedGame[]>(`${environment.apiUrl}/users/me/saved-games`);
  }

  /** Offer or accept a draw; backend returns the updated game (finished if accepted) */
  offerDraw(gameId: string): Observable<Game> {
    return this.http.post<Game>(`${this.base}/${gameId}/draw-offer`, {}).pipe(map((raw: any) => this.mapGame(raw)));
  }

  /** Resign current game */
  resign(gameId: string): Observable<Game> {
    return this.http.post<Game>(`${this.base}/${gameId}/resign`, {}).pipe(map((raw: any) => this.mapGame(raw)));
  }

  /**
   * Get position evaluation from the Java engine.
   * Returns a centipawn score and optional best move.
   */
  evaluate(gameId: string): Observable<PositionEvaluation> {
    return this.http.get<PositionEvaluation>(`${this.base}/${gameId}/evaluation`);
  }

  /**
   * Get legal moves for a piece on a given square.
   * The backend returns all legal moves for the active player as
   * { legalMoves: string[] } where each entry is "a2a3" algebraic format.
   * We filter for the clicked square and convert destinations to Square objects.
   */
  getLegalMoves(gameId: string, square: Square): Observable<Square[]> {
    return this.http.get<any>(`${this.base}/${gameId}/legal-moves`).pipe(
      map((res: any) => {
        const allMoves: string[] = Array.isArray(res) ? res : (res?.legalMoves ?? []);
        const fromAlg = String.fromCharCode(97 + square.col) + (8 - square.row);
        return allMoves
          .filter(m => m.startsWith(fromAlg))
          .map(m => ({
            col: m.charCodeAt(2) - 97,
            row: 8 - parseInt(m[3]),
          }));
      })
    );
  }
}
