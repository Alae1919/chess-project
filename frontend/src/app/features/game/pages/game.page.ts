

// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/game/pages/game.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, inject, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms'; // <-- ADDED THIS
import { Store } from '@ngrx/store';
import { combineLatest, Subscription } from 'rxjs'; // <-- Subscription added
import { ChessBoardComponent } from '../../../shared/components/chess-board/chess-board.component';
import { GameActions } from '../../../store/game/game.actions';
import { selectUser } from '../../../store/account/account.reducer'; // <-- ADDED THIS
import {
  selectCurrentGame,
  selectWhitePlayer,
  selectBlackPlayer,
  selectMoveHistory,
  selectEvaluation,
  selectChatMessages,
  selectIsAiThinking,
} from '../../../store/game/game.selectors';

@Component({
  selector: 'app-game-page',
  standalone: true,
  imports: [CommonModule, AsyncPipe, ChessBoardComponent,FormsModule],
  templateUrl: './game.page.html',
  styleUrls: ['./game.page.scss'],
})
export class GamePage implements OnInit, OnDestroy {
  @Input() id?: string;   // route param via withComponentInputBinding

  private store = inject(Store);
  private sub = new Subscription(); // To manage our current user subscription

  currentUserId?: string; // <-- ADDED THIS to fix 'currentUserId does not exist'

  vm$ = combineLatest({
    game:         this.store.select(selectCurrentGame),
    white:        this.store.select(selectWhitePlayer),
    black:        this.store.select(selectBlackPlayer),
    moves:        this.store.select(selectMoveHistory),
    evaluation:   this.store.select(selectEvaluation),
    chat:         this.store.select(selectChatMessages),
    aiThinking:   this.store.select(selectIsAiThinking),
  });

  chatInput = '';

  ngOnInit(): void {
    if (this.id) {
      this.store.dispatch(GameActions.loadGame({ gameId: this.id }));
    }
    // Fetch user ID for the chat UI
    this.sub.add(
      this.store.select(selectUser).subscribe(user => {
        this.currentUserId = user?.id;
      })
    );
  }

  ngOnDestroy(): void {
    this.store.dispatch(GameActions.resetGame());
  }

  save(): void     { this.store.dispatch(GameActions.saveGame()); }
  undo(): void     { this.store.dispatch(GameActions.undoMove()); }
  resign(): void   { if (confirm('Abandonner la partie ?')) this.store.dispatch(GameActions.resign()); }
  offerDraw(): void { this.store.dispatch(GameActions.offerDraw()); }

  sendChat(): void {
    if (!this.chatInput.trim()) return;
    this.store.dispatch(GameActions.sendChatMessage({ content: this.chatInput }));
    this.chatInput = '';
  }

  /** Convert ms to MM:SS */
  formatTime(ms: number): string {
    const total = Math.max(0, Math.floor(ms / 1000));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  /** Evaluation bar width (0–100%) */
  evalPercent(score: number): number {
    // score in centipawns; clamp to [-500, 500]
    return Math.round(((Math.min(Math.max(score, -500), 500) + 500) / 1000) * 100);
  }

  moveGroups(moves: any[]): { num: number; white: string; black?: string }[] {
    const groups: { num: number; white: string; black?: string }[] = [];
    for (let i = 0; i < moves.length; i += 2) {
      groups.push({
        num: Math.floor(i / 2) + 1,
        white: moves[i]?.algebraicNotation,
        black: moves[i + 1]?.algebraicNotation,
      });
    }
    return groups;
  }

  // --- ADDED THESE TWO METHODS TO FIX TEMPLATE ERRORS ---

  getPieceSym(piece: any): string {
    if (!piece) return '';
    const symbols: Record<string, Record<string, string>> = {
      white: { king: '♔', queen: '♕', rook: '♖', bishop: '♗', knight: '♘', pawn: '♙' },
      black: { king: '♚', queen: '♛', rook: '♜', bishop: '♝', knight: '♞', pawn: '♟' },
    };
    return symbols[piece.color]?.[piece.type] || '';
  }

  getResultLabel(result: any): string {
    if (!result) return '';
    if (!result.winner) return 'Nul (' + result.reason + ')';
    const winnerFr = result.winner === 'white' ? 'Blancs' : 'Noirs';
    return `Victoire ${winnerFr} (${result.reason})`;
  }
}