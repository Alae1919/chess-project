

// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/game/pages/game.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, HostListener, inject, OnInit, OnDestroy, Input } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms'; // <-- ADDED THIS
import { Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { combineLatest, distinctUntilChanged, map, Subscription, take } from 'rxjs'; // <-- Subscription added
import { ChessBoardComponent } from '../../../shared/components/chess-board/chess-board.component';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { BoardStylePickerComponent } from '../../../shared/components/board-style-picker/board-style-picker.component';
import { GameOverDialogComponent, RematchState } from '../../../shared/components/game-over-dialog/game-over-dialog.component';
import { playerColorOf, summarizeResult } from '../../../core/utils/game-result.utils';
import { rematchInvitation, rematchOptions } from '../../../core/utils/rematch.utils';
import { LobbyActions } from '../../../store/lobby/lobby.actions';
import { selectSentInvitation } from '../../../store/lobby/lobby.selectors';
import { GameNoticeBannerComponent } from '../../../shared/components/game-notice-banner/game-notice-banner.component';
import { WebSocketService } from '../../../core/services/websocket.service';
import { DrawOfferBannerComponent } from '../../../shared/components/draw-offer-banner/draw-offer-banner.component';
import { PromotionPickerComponent } from '../../../shared/components/promotion-picker/promotion-picker.component';
import { PromotionPiece } from '../../../core/utils/promotion.utils';
import { Game, Move } from '../../../core/models';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
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
  selectPendingPromotion,
  selectNotice,
  selectOpponentAway,
} from '../../../store/game/game.selectors';

const VIEW_KEY = 'rex_board_view';

@Component({
  selector: 'app-game-page',
  standalone: true,
  imports: [CommonModule, AsyncPipe, RouterLink, ChessBoardComponent, ChessBoard3DComponent, BoardStylePickerComponent, PromotionPickerComponent, DrawOfferBannerComponent, GameNoticeBannerComponent, GameOverDialogComponent, FormsModule],
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

  readonly notice$ = this.store.select(selectNotice);
  readonly sentInvitation$ = this.store.select(selectSentInvitation);
  /** The game whose result dialog was closed, so it stays closed until the next game */
  private dismissedFor: string | null = null;
  private rematchRequested = false;
  readonly opponentAway$ = this.store.select(selectOpponentAway);
  private gameSocket = inject(WebSocketService);
  /** 'reconnecting' while our own connection is down */
  readonly connection$ = this.gameSocket.connectionState$;

  /** Pawn move waiting for its promotion piece (shown over either board) */
  readonly pendingPromotion$ = this.store.select(selectPendingPromotion);

  private boardPrefs = inject(BoardPrefsService);
  /** Which board to draw (3D or 2D, falling back to 2D without WebGL) and its style */
  readonly boardView$ = this.boardPrefs.view$;
  settingsOpen = false;

  /** Canvas shape of the 3D board: wide on desktop, square on phones */
  boardAspect   = window.innerWidth <= 768 ? 1 : 1.5;
  boardFlipped  = false;
  /** Straight-down view of the 3D board; remembered between games */
  topView       = this.readViewPreference();
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
    // A new game starts with a fresh result dialog and no rematch pending
    this.sub.add(
      this.store.select(selectCurrentGame).pipe(map((g) => g?.id), distinctUntilChanged()).subscribe(() => {
        this.dismissedFor = null;
        this.rematchRequested = false;
      })
    );
    this.sub.add(
      this.store.select(selectUser).subscribe(user => {
        this.currentUserId = user?.id;
      })
    );
  }

  @HostListener('window:resize')
  onResize(): void { this.boardAspect = window.innerWidth <= 768 ? 1 : 1.5; }

  toggleSettings(): void { this.settingsOpen = !this.settingsOpen; }

  @HostListener('document:keydown.escape')
  closeSettings(): void { this.settingsOpen = false; }

  toggleView(): void { this.setTopView(!this.topView); }

  setTopView(top: boolean): void {
    this.topView = top;
    try { localStorage.setItem(VIEW_KEY, this.topView ? 'top' : '3d'); } catch { /* storage unavailable */ }
  }

  private readViewPreference(): boolean {
    try { return localStorage.getItem(VIEW_KEY) === 'top'; } catch { return false; }
  }

  flipBoard():       void { this.boardFlipped  = !this.boardFlipped; }
  toggleLeft():       void { this.leftOpen      = !this.leftOpen; }
  toggleRight():      void { this.rightOpen     = !this.rightOpen; }
  toggleMobileChat(): void { this.mobileChatOpen = !this.mobileChatOpen; }

  ngOnDestroy(): void {
    // The game stays in the store so the navbar can offer to resume it; it is
    // replaced on the next create/load. The socket does not: leaving the page
    // must not keep the player "connected" to a game they have walked away from.
    this.gameSocket.disconnect();
    this.sub.unsubscribe();
  }

  save(): void     { this.store.dispatch(GameActions.saveGame()); }
  undo(): void     { this.store.dispatch(GameActions.undoMove()); }
  resign(): void   { if (confirm('Abandonner la partie ?')) this.store.dispatch(GameActions.resign()); }
  offerDraw(): void { this.store.dispatch(GameActions.offerDraw()); }
  answerDraw(accepted: boolean): void { this.store.dispatch(GameActions.drawResponse({ accepted })); }
  dismissNotice(): void { this.store.dispatch(GameActions.dismissNotice()); }

  // ── End of game ───────────────────────────────────────────────────────────
  myColor(game: Game) { return playerColorOf(game, this.currentUserId); }
  resultSummary(game: Game) { return summarizeResult(game, this.myColor(game)); }
  showResultDialog(game: Game): boolean { return !!this.resultSummary(game) && this.dismissedFor !== game.id; }
  closeResultDialog(game: Game): void { this.dismissedFor = game.id; }
  reopenResultDialog(): void { this.dismissedFor = null; }

  rematchState(sent: unknown): RematchState {
    return sent ? 'pending' : this.rematchRequested ? 'declined' : 'idle';
  }

  /** Same opponent and clock, colours swapped: an invitation online, a new game otherwise. */
  rematch(game: Game): void {
    const mine = this.myColor(game);
    if (game.mode === 'online') {
      if (!mine) return;
      this.rematchRequested = true;
      this.store.dispatch(LobbyActions.sendInvitation({ req: rematchInvitation(game, mine) }));
      return;
    }
    this.store.dispatch(GameActions.createGame({ options: rematchOptions(game, mine) }));
  }

  newGame(): void { this.router.navigate(['/home']); }
  toLobby(): void { this.router.navigate(['/online']); }

  /**
   * The open draw offer from this player's point of view: 'incoming' from the
   * opponent (answer it), 'outgoing' from them (wait). None in local games, where
   * a draw is agreed at once.
   */
  drawOfferKind(game: Game): 'incoming' | 'outgoing' | null {
    const by = game.drawOfferedBy;
    if (!by || game.mode === 'local') return null;
    const mine = [game.playerWhite, game.playerBlack].find((p) => p.userId && p.userId === this.currentUserId);
    return mine?.color === by ? 'outgoing' : 'incoming';
  }

  promote(move: Omit<Move, 'algebraicNotation' | 'timestamp'>, piece: PromotionPiece): void {
    this.store.dispatch(GameActions.submitMove({ move: { ...move, promotion: piece } }));
  }
  cancelPromotion(): void { this.store.dispatch(GameActions.cancelPromotion()); }

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
}