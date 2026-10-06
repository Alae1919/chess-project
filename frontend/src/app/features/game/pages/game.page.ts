

// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/game/pages/game.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { AfterViewChecked, Component, ElementRef, HostListener, inject, OnInit, OnDestroy, Input, ViewChild } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms'; // <-- ADDED THIS
import { Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { combineLatest, distinctUntilChanged, filter, map, pairwise, Subscription, take } from 'rxjs'; // <-- Subscription added
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
import { Game, GamePlayer, Move } from '../../../core/models';
import { SheetDragDirective } from '../../../shared/directives/sheet-drag.directive';
import { isCompactViewport, vibrate } from '../../../core/utils/viewport';
import { aiLevelLabel } from '../../../core/utils/ai-levels';
import { formatClock, isLowTime } from '../../../core/utils/clock.utils';
import { PlayerStripComponent } from '../components/player-strip.component';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { GameActions } from '../../../store/game/game.actions';
import { selectUser } from '../../../store/account/account.reducer'; // <-- ADDED THIS
import { isPlayableStatus, isTerminalStatus } from '../../../core/utils/game-status.utils';
import {
  selectCurrentGame,
  selectWhitePlayer,
  selectBlackPlayer,
  selectMoveHistory,
  selectAnalysis,
  selectEvaluation,
  selectHint,
  selectChatMessages,
  selectIsAiThinking,
  selectIsLoading,
  selectPendingPromotion,
  selectNotice,
  selectOpponentAway,
} from '../../../store/game/game.selectors';

const VIEW_KEY = 'rex_board_view';

/** The bottom sheets of the phone layout */
export type GameSheet = 'more' | 'notation' | 'chat';

/** How long the resign button must be held on a phone */
export const RESIGN_HOLD_MS = 1000;

/**
 * Canvas shape of the 3D board. The tilted board is wider than it is tall, so a wide canvas
 * wastes the least: 1.5 on a desktop, a little wider than square on an upright phone, square
 * on a phone on its side, where the height is what runs out.
 */
export function boardAspectFor(): number {
  if (!isCompactViewport()) return 1.5;
  return window.innerWidth < window.innerHeight ? 1.25 : 1;
}

@Component({
  selector: 'app-game-page',
  standalone: true,
  imports: [CommonModule, AsyncPipe, RouterLink, ChessBoardComponent, ChessBoard3DComponent, BoardStylePickerComponent, PromotionPickerComponent, DrawOfferBannerComponent, GameNoticeBannerComponent, GameOverDialogComponent, FormsModule, SheetDragDirective, PlayerStripComponent],
  templateUrl: './game.page.html',
  styleUrls: ['./game.page.scss'],
})
export class GamePage implements OnInit, OnDestroy, AfterViewChecked {
  /**
   * The game's id, a route param bound to this input. The router reuses this component when
   * only the id changes (a rematch, an accepted invitation), so a change has to load the new
   * game here: ngOnInit runs only once.
   */
  @Input() set id(value: string | undefined) {
    if (value === this._id) return;
    this._id = value;
    if (this.started && value) this.open(value);
  }
  get id(): string | undefined { return this._id; }
  private _id?: string;
  private started = false;

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
    analysis:     this.store.select(selectAnalysis),
    hint:         this.store.select(selectHint),
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

  /** Canvas shape of the 3D board (see boardAspectFor) */
  boardAspect   = boardAspectFor();
  /** The compact layout is in use (read when the board is created) */
  readonly compact = isCompactViewport();
  boardFlipped  = false;
  /** Straight-down view of the 3D board; remembered between games */
  topView       = this.readViewPreference();
  rightTab: 'notation' | 'chat' = 'notation';
  chatInput     = '';
  leftOpen      = true;
  rightOpen     = true;

  /** The open bottom sheet on a phone, if any */
  sheet: GameSheet | null = null;
  /** The resign button is being held down */
  resignHolding = false;
  private resignTimer?: ReturnType<typeof setTimeout>;

  @ViewChild('movesStrip') private movesStrip?: ElementRef<HTMLElement>;
  private movesStripSize = 0;

  ngOnInit(): void {
    const mobile = isCompactViewport();
    this.leftOpen  = !mobile;
    this.rightOpen = !mobile;
    this.started = true;

    if (this.id) {
      this.open(this.id);
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
    // A light buzz for every move on a phone, a double one for check
    this.sub.add(
      this.store.select(selectCurrentGame).pipe(
        filter((g): g is Game => !!g),
        map((g) => ({ id: g.id, moves: g.moves.length, check: g.status === 'check' || g.status === 'checkmate' })),
        pairwise(),
      ).subscribe(([before, after]) => {
        if (before.id === after.id && after.moves > before.moves && isCompactViewport()) {
          vibrate(after.check ? [12, 60, 12] : 10);
        }
      })
    );
    this.sub.add(
      this.store.select(selectUser).subscribe(user => {
        this.currentUserId = user?.id;
      })
    );
    // Black at the top is the other player's view: show each player their own side. Decided when
    // a game (or the profile that tells us who "me" is) first appears, so it never overrides
    // a board the player has turned themselves.
    this.sub.add(
      combineLatest([this.store.select(selectCurrentGame), this.store.select(selectUser)]).pipe(
        filter(([game]) => !!game),
        distinctUntilChanged(([g1, u1], [g2, u2]) => g1!.id === g2!.id && u1?.id === u2?.id),
      ).subscribe(([game, user]) => {
        if (game!.id !== this.orientedFor) { this.orientedFor = game!.id; this.flippedByPlayer = false; }
        if (!this.flippedByPlayer) this.boardFlipped = playerColorOf(game!, user?.id) === 'black';
      })
    );
  }

  /** Starts showing a game: a fresh view, then the game itself. */
  private open(id: string): void {
    this.settingsOpen = false;
    this.closeSheet();
    this.boardFlipped = false;
    this.flippedByPlayer = false;
    this.store.dispatch(GameActions.loadGame({ gameId: id }));
  }

  /** The game whose orientation was last chosen, and whether the player turned the board since. */
  private orientedFor: string | null = null;
  private flippedByPlayer = false;

  /** Online games have no undo (the server refuses it), and a finished game can't be reopened. */
  /** The engine's opinion is off limits during a live online game; afterwards it is fair analysis. */
  canAnalyse(game: Game): boolean {
    return game.mode !== 'online' || !isPlayableStatus(game.status);
  }

  /** A hint is for the player to move in a live game against the AI or on their own. */
  canHint(game: Game, busy: boolean): boolean {
    return game.mode !== 'online' && isPlayableStatus(game.status) && !busy;
  }

  toggleAnalysis(): void { this.store.dispatch(GameActions.toggleAnalysis()); }
  requestHint(): void { this.store.dispatch(GameActions.requestHint()); }

  /** "e2e4" -> "e2 → e4"; a promotion "e7e8q" -> "e7 → e8 (=D)" */
  hintText(uci: string): string {
    const promotion: Record<string, string> = { q: 'Dame', r: 'Tour', b: 'Fou', n: 'Cavalier' };
    const suffix = uci.length > 4 ? ` (${promotion[uci[4]] ?? uci[4]})` : '';
    return `${uci.slice(0, 2)} → ${uci.slice(2, 4)}${suffix}`;
  }

  canUndo(game: Game): boolean {
    return game.mode !== 'online' && isPlayableStatus(game.status);
  }

  @HostListener('window:resize')
  onResize(): void { this.boardAspect = boardAspectFor(); }

  toggleSettings(): void { this.settingsOpen = !this.settingsOpen; }

  @HostListener('document:keydown.escape')
  closeSettings(): void { this.settingsOpen = false; this.closeSheet(); }

  // ── Phone layout ──────────────────────────────────────────────────────────
  openSheet(sheet: GameSheet): void { this.settingsOpen = false; this.sheet = sheet; }

  closeSheet(): void { this.sheet = null; this.cancelResignHold(); }

  /** Upright, the board shows its far side's player above it and its near side's below */
  topPlayer(vm: { white: GamePlayer | null; black: GamePlayer | null }): GamePlayer | null {
    return this.boardFlipped ? vm.white : vm.black;
  }

  bottomPlayer(vm: { white: GamePlayer | null; black: GamePlayer | null }): GamePlayer | null {
    return this.boardFlipped ? vm.black : vm.white;
  }

  /** The strip of the person holding the phone: "Vous" (never in a local game, where both are) */
  isMe(player: GamePlayer, game: Game): boolean {
    return game.mode !== 'local' && !!this.currentUserId && player.userId === this.currentUserId;
  }

  modeLabel(game: Game): string {
    const labels: Record<string, string> = { ai: "Contre l'IA", local: 'Partie locale', online: 'En ligne' };
    return labels[game.mode] ?? 'Partie';
  }

  /** "Rapide 10′" */
  timeControlLabel(game: Game): string {
    const names: Record<string, string> = { blitz: 'Blitz', rapid: 'Rapide', classical: 'Classique' };
    const tc = game.timeControl;
    if (!tc) return '';
    const minutes = Math.round(tc.initialMs / 60_000);
    return `${names[tc.type] ?? tc.type} ${minutes}′`;
  }

  /** Who this game is against, as the sheets title it */
  opponentLabel(vm: { game: Game | null; white: GamePlayer | null; black: GamePlayer | null }): string {
    const game = vm.game;
    if (!game) return '';
    if (game.mode === 'local') return 'Partie locale';
    const mine = this.myColor(game);
    const them = mine === 'white' ? vm.black : mine === 'black' ? vm.white : vm.black;
    if (!them) return '';
    return them.isAi ? `vs IA · ${aiLevelLabel(them.aiDifficulty)}` : `vs ${them.username}`;
  }

  /** "IA · Maître", "marco": the opponent without the "vs" */
  opponentName(vm: { game: Game | null; white: GamePlayer | null; black: GamePlayer | null }): string {
    return this.opponentLabel(vm).replace(/^vs /, '');
  }

  /** Resigning on a phone takes a press held for a second: a tap in the wrong place costs nothing */
  startResignHold(event: PointerEvent): void {
    if (event.button !== 0) return;
    this.cancelResignHold();
    this.resignHolding = true;
    this.resignTimer = setTimeout(() => {
      this.resignHolding = false;
      vibrate([15, 40, 15]);
      this.store.dispatch(GameActions.resign());
      this.closeSheet();
    }, RESIGN_HOLD_MS);
  }

  cancelResignHold(): void {
    clearTimeout(this.resignTimer);
    this.resignHolding = false;
  }

  /** A keyboard can't hold a button down: Enter or Space asks instead */
  resignByKeyboard(event: MouseEvent): void {
    if (event.detail === 0) this.resign();
  }

  /** Keep the latest move in view in the moves strip */
  ngAfterViewChecked(): void {
    const el = this.movesStrip?.nativeElement;
    if (!el) return;
    const size = el.scrollWidth + el.scrollHeight;
    if (size !== this.movesStripSize) {
      this.movesStripSize = size;
      el.scrollLeft = el.scrollWidth;
      el.scrollTop = el.scrollHeight;
    }
  }

  toggleView(): void { this.setTopView(!this.topView); }

  setTopView(top: boolean): void {
    this.topView = top;
    try { localStorage.setItem(VIEW_KEY, this.topView ? 'top' : '3d'); } catch { /* storage unavailable */ }
  }

  private readViewPreference(): boolean {
    try { return localStorage.getItem(VIEW_KEY) === 'top'; } catch { return false; }
  }

  flipBoard():       void { this.boardFlipped  = !this.boardFlipped; this.flippedByPlayer = true; }
  toggleLeft():       void { this.leftOpen      = !this.leftOpen; }
  toggleRight():      void { this.rightOpen     = !this.rightOpen; }

  ngOnDestroy(): void {
    // The game stays in the store so the navbar can offer to resume it; it is
    // replaced on the next create/load. The socket does not: leaving the page
    // must not keep the player "connected" to a game they have walked away from.
    this.gameSocket.disconnect();
    this.sub.unsubscribe();
    clearTimeout(this.resignTimer);
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

  /**
   * Goes back to the home page. Walking away from an online game that is still going starts
   * the forfeit countdown, so that gets a confirmation; the others cost nothing.
   */
  leave(game: Game): void {
    if (game.mode === 'online' && isPlayableStatus(game.status)
        && !confirm('Quitter la partie en cours ? Vous la perdrez si vous ne revenez pas à temps.')) {
      return;
    }
    this.router.navigate(['/home']);
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
  formatTime(ms: number): string { return formatClock(ms); }

  /** Under 30 s on the clock */
  isLowTime(ms?: number): boolean { return isLowTime(ms); }

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