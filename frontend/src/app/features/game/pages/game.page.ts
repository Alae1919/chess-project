

// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/game/pages/game.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, HostListener, inject, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms'; // <-- ADDED THIS
import { Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { combineLatest, Subscription, take } from 'rxjs'; // <-- Subscription added
import { ChessBoardComponent } from '../../../shared/components/chess-board/chess-board.component';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { isWebGLAvailable } from '../../../shared/three/luxe-board-scene';
import { GameActions } from '../../../store/game/game.actions';
import { selectUser } from '../../../store/account/account.reducer'; // <-- ADDED THIS
import { isTerminalStatus } from '../../../core/utils/game-status.utils';
import {
  selectCurrentGame,
  selectWhitePlayer,
  selectBlackPlayer,
  selectMoveHistory,
  selectEvaluation,
  selectChatMessages,
  selectIsAiThinking,
  selectIsLoading,
} from '../../../store/game/game.selectors';

@Component({
  selector: 'app-game-page',
  standalone: true,
  imports: [CommonModule, AsyncPipe, RouterLink, ChessBoardComponent, ChessBoard3DComponent, FormsModule],
  templateUrl: './game.page.html',
  styleUrls: ['./game.page.scss'],
})
export class GamePage implements OnInit, OnDestroy {
  @Input() id?: string;   // route param via withComponentInputBinding

  private store = inject(Store);
  private router = inject(Router);
  private sub = new Subscription(); // To manage our current user subscription

  currentUserId?: string; // <-- ADDED THIS to fix 'currentUserId does not exist'
  isTerminal = isTerminalStatus;

  vm$ = combineLatest({
    game:         this.store.select(selectCurrentGame),
    white:        this.store.select(selectWhitePlayer),
    black:        this.store.select(selectBlackPlayer),
    moves:        this.store.select(selectMoveHistory),
    evaluation:   this.store.select(selectEvaluation),
    chat:         this.store.select(selectChatMessages),
    aiThinking:   this.store.select(selectIsAiThinking),
    loading:      this.store.select(selectIsLoading),
  });

  /** Falls back to the flat 2D board when the browser can't do WebGL */
  readonly webgl = isWebGLAvailable();

  /** Canvas shape of the 3D board: wide on desktop, square on phones */
  boardAspect   = window.innerWidth <= 768 ? 1 : 1.5;
  boardFlipped  = false;
  rightTab: 'notation' | 'chat' = 'notation';
  chatInput     = '';
  leftOpen      = true;
  rightOpen     = true;
  mobileChatOpen = false;

  ngOnInit(): void {
    const mobile = window.innerWidth <= 768;
    this.leftOpen  = !mobile;
    this.rightOpen = !mobile;

    if (this.id) {
      this.store.dispatch(GameActions.loadGame({ gameId: this.id }));
    } else {
      // /game without id: resume the current game if there is one, otherwise nothing to show
      this.sub.add(
        this.store.select(selectCurrentGame).pipe(take(1)).subscribe((game) => {
          this.router.navigate(game ? ['/game', game.id] : ['/home'], { replaceUrl: true });
        })
      );
    }
    this.sub.add(
      this.store.select(selectUser).subscribe(user => {
        this.currentUserId = user?.id;
      })
    );
  }

  @HostListener('window:resize')
  onResize(): void { this.boardAspect = window.innerWidth <= 768 ? 1 : 1.5; }

  flipBoard():       void { this.boardFlipped  = !this.boardFlipped; }
  toggleLeft():       void { this.leftOpen      = !this.leftOpen; }
  toggleRight():      void { this.rightOpen     = !this.rightOpen; }
  toggleMobileChat(): void { this.mobileChatOpen = !this.mobileChatOpen; }

  ngOnDestroy(): void {
    // The game stays in the store so the navbar can offer to resume it; it is
    // replaced on the next create/load.
    this.sub.unsubscribe();
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

  /** Under 30 s on the clock */
  isLowTime(ms?: number): boolean {
    return ms !== undefined && ms > 0 && ms < 30_000;
  }

  /** Evaluation bar share for White (0–100%) */
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
    // U+FE0E forces text (not emoji) rendering of the pawn glyphs
    const sym = symbols[piece.color]?.[piece.type];
    return sym ? sym + '︎' : '';
  }

  getResultLabel(result: any): string {
    if (!result) return '';
    if (!result.winner) return 'Nul (' + result.reason + ')';
    const winnerFr = result.winner === 'white' ? 'Blancs' : 'Noirs';
    return `Victoire ${winnerFr} (${result.reason})`;
  }
}