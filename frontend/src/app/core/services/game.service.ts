// src/app/core/services/game.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Game,
  GameOptions,
  Move,
  SavedGame,
  PositionEvaluation,
  Square,
} from '../models';

@Injectable({ providedIn: 'root' })
export class GameService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/games`;

  /** Create a new game with chosen options */
  createGame(options: GameOptions): Observable<Game> {
    return this.http.post<Game>(this.base, options);
  }

  /** Get a game by ID */
  getGame(gameId: string): Observable<Game> {
    return this.http.get<Game>(`${this.base}/${gameId}`);
  }

  /**
   * Submit a move to the Java backend.
   * The backend validates legality and returns the updated game state.
   */
  submitMove(gameId: string, move: Omit<Move, 'algebraicNotation' | 'timestamp'>): Observable<Game> {
    return this.http.post<Game>(`${this.base}/${gameId}/moves`, move);
  }

  /** Request the AI to compute and return its move */
  getAiMove(gameId: string): Observable<Move> {
    return this.http.post<Move>(`${this.base}/${gameId}/ai-move`, {});
  }

  /** Undo the last move (if allowed by game options) */
  undoMove(gameId: string): Observable<Game> {
    return this.http.delete<Game>(`${this.base}/${gameId}/moves/last`);
  }

  /** Save current game state */
  saveGame(gameId: string): Observable<SavedGame> {
    return this.http.post<SavedGame>(`${this.base}/${gameId}/save`, {});
  }

  /** Load all saved games for the current user */
  getSavedGames(): Observable<SavedGame[]> {
    return this.http.get<SavedGame[]>(`${this.base}/saved`);
  }

  /** Offer or accept a draw */
  offerDraw(gameId: string): Observable<{ accepted: boolean }> {
    return this.http.post<{ accepted: boolean }>(`${this.base}/${gameId}/draw`, {});
  }

  /** Resign current game */
  resign(gameId: string): Observable<Game> {
    return this.http.post<Game>(`${this.base}/${gameId}/resign`, {});
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
   * Used to highlight possible moves in the UI.
   */
  getLegalMoves(gameId: string, square: Square): Observable<Square[]> {
    const params = new HttpParams()
      .set('row', square.row)
      .set('col', square.col);
    return this.http.get<Square[]>(`${this.base}/${gameId}/legal-moves`, { params });
  }
}
