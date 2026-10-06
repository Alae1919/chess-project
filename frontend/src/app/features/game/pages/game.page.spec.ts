import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { of } from 'rxjs';
import { Game, User } from '../../../core/models';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { BoardStylePickerComponent } from '../../../shared/components/board-style-picker/board-style-picker.component';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { ChessBoardComponent } from '../../../shared/components/chess-board/chess-board.component';
import { DrawOfferBannerComponent } from '../../../shared/components/draw-offer-banner/draw-offer-banner.component';
import { GameNoticeBannerComponent } from '../../../shared/components/game-notice-banner/game-notice-banner.component';
import { GameOverDialogComponent } from '../../../shared/components/game-over-dialog/game-over-dialog.component';
import { PromotionPickerComponent } from '../../../shared/components/promotion-picker/promotion-picker.component';
import { makeGame } from '../../../testing/game-fixtures';
import { initialAccountState } from '../../../store/account/account.reducer';
import { GameActions } from '../../../store/game/game.actions';
import { initialGameState } from '../../../store/game/game.state';
import { initialLobbyState } from '../../../store/lobby/lobby.state';
import { GamePage, RESIGN_HOLD_MS } from './game.page';

describe('GamePage', () => {
  let fixture: ComponentFixture<GamePage>;
  let page: GamePage;
  let store: MockStore;
  let dispatch: jasmine.Spy;

  const me = { id: 'me', username: 'me' } as User;
  const human = (color: 'white' | 'black', userId: string) =>
    ({ username: color, color, timeRemainingMs: 0, capturedPieces: [], userId });
  const bot = (color: 'white' | 'black') =>
    ({ username: 'AI', color, timeRemainingMs: 0, capturedPieces: [], isAi: true });

  const state = (game: Game | null, user: User | null = me) => ({
    game: { ...initialGameState, currentGame: game },
    account: { ...initialAccountState, user },
    lobby: initialLobbyState,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GamePage],
      providers: [
        provideMockStore({ initialState: state(null) }),
        provideRouter([]),
        { provide: WebSocketService, useValue: { connectionState$: of('open'), disconnect: () => {}, connect: () => {} } },
        { provide: BoardPrefsService, useValue: { view$: of({ mode: '2d', prefs: { style2d: 'classic-wood', style3d: 'marble-gold', mode: '2d' } }) } },
      ],
    });
    // The boards, banners and dialogs have their own specs; here only the page's own behaviour matters
    TestBed.overrideComponent(GamePage, {
      remove: {
        imports: [ChessBoardComponent, ChessBoard3DComponent, BoardStylePickerComponent, PromotionPickerComponent,
                  DrawOfferBannerComponent, GameNoticeBannerComponent, GameOverDialogComponent],
      },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    store = TestBed.inject(MockStore);
    dispatch = spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(GamePage);
    page = fixture.componentInstance;
  });

  function open(id: string) {
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  function show(game: Game | null, user: User | null = me) {
    store.setState(state(game, user));
    fixture.detectChanges();
  }

  describe('opening a game', () => {
    it('loads the game it was opened with', () => {
      open('game-a');

      expect(dispatch).toHaveBeenCalledWith(GameActions.loadGame({ gameId: 'game-a' }));
    });

    it('loads the next game when only the address changes: a rematch, an accepted invitation', () => {
      open('game-a');
      dispatch.calls.reset();

      fixture.componentRef.setInput('id', 'game-b');
      fixture.detectChanges();

      expect(dispatch).toHaveBeenCalledWith(GameActions.loadGame({ gameId: 'game-b' }));
    });

    it('does not load the same game twice', () => {
      open('game-a');
      dispatch.calls.reset();

      fixture.componentRef.setInput('id', 'game-a');
      fixture.detectChanges();

      expect(dispatch).not.toHaveBeenCalledWith(GameActions.loadGame({ gameId: 'game-a' }));
    });

    it('starts the next game with the settings panel closed and the board the right way up', () => {
      open('game-a');
      page.settingsOpen = true;
      page.boardFlipped = true;

      fixture.componentRef.setInput('id', 'game-b');
      fixture.detectChanges();

      expect(page.settingsOpen).toBeFalse();
      expect(page.boardFlipped).toBeFalse();
    });
  });

  describe('which way up the board is', () => {
    it('shows Black\'s side to a player of Black', () => {
      open('game-1');

      show(makeGame({ mode: 'online', playerWhite: human('white', 'them'), playerBlack: human('black', 'me') }));

      expect(page.boardFlipped).toBeTrue();
    });

    it('keeps White at the bottom for a player of White, and in a local game', () => {
      open('game-1');

      show(makeGame({ mode: 'online', playerWhite: human('white', 'me'), playerBlack: human('black', 'them') }));
      expect(page.boardFlipped).toBeFalse();

      show(makeGame({ id: 'game-2', mode: 'local' }));
      expect(page.boardFlipped).toBeFalse();
    });

    it('turns the board for a human who is Black against the AI', () => {
      open('game-1');

      show(makeGame({ mode: 'ai', playerWhite: bot('white'), playerBlack: human('black', 'me') }));

      expect(page.boardFlipped).toBeTrue();
    });

    it('leaves it alone once the player has turned it themselves', () => {
      open('game-1');
      show(makeGame({ mode: 'online', playerWhite: human('white', 'me'), playerBlack: human('black', 'them') }));

      page.flipBoard();                                   // the player wants Black's view
      show(makeGame({ mode: 'online', playerWhite: human('white', 'me'), playerBlack: human('black', 'them'), currentTurn: 'black' }));

      expect(page.boardFlipped).toBeTrue();               // a new position of the same game doesn't undo it
    });

    it('sets it again for the next game', () => {
      open('game-1');
      show(makeGame({ mode: 'online', playerWhite: human('white', 'them'), playerBlack: human('black', 'me') }));
      expect(page.boardFlipped).toBeTrue();

      show(makeGame({ id: 'game-2', mode: 'online', playerWhite: human('white', 'me'), playerBlack: human('black', 'them') }));

      expect(page.boardFlipped).toBeFalse();
    });
  });

  describe('undo', () => {
    it('is not offered in an online game: the server refuses it', () => {
      expect(page.canUndo(makeGame({ mode: 'online' }))).toBeFalse();
    });

    it('is offered against the AI and in a local game', () => {
      expect(page.canUndo(makeGame({ mode: 'ai' }))).toBeTrue();
      expect(page.canUndo(makeGame({ mode: 'local' }))).toBeTrue();
    });

    it('is not offered once the game is over', () => {
      expect(page.canUndo(makeGame({ mode: 'ai', status: 'checkmate' }))).toBeFalse();
    });
  });


  describe('leaving', () => {
    let confirmSpy: jasmine.Spy;
    let navigate: jasmine.Spy;

    beforeEach(() => {
      confirmSpy = spyOn(window, 'confirm');
      navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    });

    it('asks first in an online game that is still going: walking away forfeits it', () => {
      confirmSpy.and.returnValue(false);

      page.leave(makeGame({ mode: 'online', status: 'active' }));

      expect(confirmSpy).toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('leaves when the player confirms', () => {
      confirmSpy.and.returnValue(true);

      page.leave(makeGame({ mode: 'online', status: 'active' }));

      expect(navigate).toHaveBeenCalledWith(['/home']);
    });

    it('leaves straight away from a game against the AI, a local game, or a finished game', () => {
      page.leave(makeGame({ mode: 'ai', status: 'active' }));
      page.leave(makeGame({ mode: 'local', status: 'active' }));
      page.leave(makeGame({ mode: 'online', status: 'checkmate' }));

      expect(confirmSpy).not.toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledTimes(3);
    });
  });

  describe('analysis and hints', () => {
    it('offers analysis everywhere except a live online game', () => {
      expect(page.canAnalyse(makeGame({ mode: 'ai', status: 'active' }))).toBeTrue();
      expect(page.canAnalyse(makeGame({ mode: 'local', status: 'active' }))).toBeTrue();
      expect(page.canAnalyse(makeGame({ mode: 'online', status: 'active' }))).toBeFalse();
      expect(page.canAnalyse(makeGame({ mode: 'online', status: 'checkmate' }))).toBeTrue();
    });

    it('offers a hint only in a live game that is not online, and not while something is in progress', () => {
      expect(page.canHint(makeGame({ mode: 'ai', status: 'active' }), false)).toBeTrue();
      expect(page.canHint(makeGame({ mode: 'ai', status: 'active' }), true)).toBeFalse();
      expect(page.canHint(makeGame({ mode: 'online', status: 'active' }), false)).toBeFalse();
      expect(page.canHint(makeGame({ mode: 'local', status: 'checkmate' }), false)).toBeFalse();
    });

    it('turns the toggle and the button into actions', () => {
      page.toggleAnalysis();
      page.requestHint();

      expect(dispatch).toHaveBeenCalledWith(GameActions.toggleAnalysis());
      expect(dispatch).toHaveBeenCalledWith(GameActions.requestHint());
    });

    it('writes a hint as a move a player can read', () => {
      expect(page.hintText('e2e4')).toBe('e2 → e4');
      expect(page.hintText('e7e8q')).toBe('e7 → e8 (Dame)');
      expect(page.hintText('a2a1n')).toBe('a2 → a1 (Cavalier)');
    });

    it('shows the hint and the Analyse and Indice buttons for a game against the AI', () => {
      open('game-1');
      page.leftOpen = true;   // the side panel starts closed in a window as narrow as the test browser's
      const game = makeGame({ mode: 'ai', playerWhite: human('white', 'me'), playerBlack: bot('black') });
      store.setState({ ...state(game), game: { ...initialGameState, currentGame: game, hint: 'g1f3', analysis: true } });
      fixture.detectChanges();

      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('g1 → f3');
      expect(text).toContain('Analyse');
      expect(text).toContain('Indice');
    });
  });

  describe('on a phone', () => {
    const press = (button = 0) => ({ button } as PointerEvent);

    it("shows the far side's player above the board and the near side's below, turning with it", () => {
      const vm = { white: human('white', 'me'), black: bot('black') };

      expect(page.topPlayer(vm)).toBe(vm.black);
      expect(page.bottomPlayer(vm)).toBe(vm.white);

      page.flipBoard();

      expect(page.topPlayer(vm)).toBe(vm.white);
      expect(page.bottomPlayer(vm)).toBe(vm.black);
    });

    it('names the time control and the opponent the way the header and the sheets show them', () => {
      expect(page.timeControlLabel(makeGame({ timeControl: { type: 'rapid', initialMs: 600_000, incrementMs: 0 } }))).toBe('Rapide 10′');

      const vsAi = makeGame({ mode: 'ai', playerWhite: human('white', 'me'), playerBlack: { ...bot('black'), aiDifficulty: 5 } });
      expect(page.opponentLabel({ game: vsAi, white: vsAi.playerWhite, black: vsAi.playerBlack })).toBe('vs IA · Maître');
    });

    it('resigns only once the button has been held for a full second', fakeAsync(() => {
      page.startResignHold(press());
      tick(RESIGN_HOLD_MS - 1);
      expect(dispatch).not.toHaveBeenCalledWith(GameActions.resign());

      tick(1);
      expect(dispatch).toHaveBeenCalledWith(GameActions.resign());
      expect(page.sheet).toBeNull();
    }));

    it('does not resign when the finger lifts too soon', fakeAsync(() => {
      page.startResignHold(press());
      tick(RESIGN_HOLD_MS / 2);
      page.cancelResignHold();
      tick(RESIGN_HOLD_MS);

      expect(dispatch).not.toHaveBeenCalledWith(GameActions.resign());
      expect(page.resignHolding).toBeFalse();
    }));

    it('ignores a right click, and asks a keyboard user instead of making them hold', fakeAsync(() => {
      const confirmSpy = spyOn(window, 'confirm').and.returnValue(true);

      page.startResignHold(press(2));
      tick(RESIGN_HOLD_MS);
      expect(dispatch).not.toHaveBeenCalledWith(GameActions.resign());

      page.resignByKeyboard({ detail: 1 } as MouseEvent);   // the click that ends a pointer hold
      expect(confirmSpy).not.toHaveBeenCalled();

      page.resignByKeyboard({ detail: 0 } as MouseEvent);   // Enter or Space
      expect(confirmSpy).toHaveBeenCalled();
      expect(dispatch).toHaveBeenCalledWith(GameActions.resign());
    }));

    it('says "Vous" only for the signed-in player, and never in a local game', () => {
      open('game-1');
      page.currentUserId = 'me';
      const online = makeGame({ mode: 'online', playerWhite: human('white', 'me'), playerBlack: human('black', 'them') });

      expect(page.isMe(online.playerWhite, online)).toBeTrue();
      expect(page.isMe(online.playerBlack, online)).toBeFalse();
      expect(page.isMe(online.playerWhite, { ...online, mode: 'local' })).toBeFalse();
    });

    it('opens one sheet at a time, and Escape closes it', () => {
      page.openSheet('more');
      page.openSheet('notation');
      expect(page.sheet).toBe('notation');

      page.closeSettings();   // the Escape handler
      expect(page.sheet).toBeNull();
    });

    it('starts the next game with no sheet open', () => {
      open('game-a');
      page.openSheet('more');

      fixture.componentRef.setInput('id', 'game-b');
      fixture.detectChanges();

      expect(page.sheet).toBeNull();
    });
  });
});
