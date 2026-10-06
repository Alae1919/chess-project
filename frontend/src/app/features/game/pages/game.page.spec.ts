import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
import { GamePage } from './game.page';

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
});
