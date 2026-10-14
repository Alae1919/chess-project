import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { AuthService } from '../../../core/services/auth.service';
import { GameActions } from '../../../store/game/game.actions';
import { initialGameState } from '../../../store/game/game.state';
import { SavedGame, User } from '../../../core/models';
import { makeGame } from '../../../testing/game-fixtures';
import { HomePage, resumeCard } from './home.page';

describe('HomePage', () => {
  let fixture: ComponentFixture<HomePage>;
  let page: HomePage;
  let store: MockStore;
  let dispatch: jasmine.Spy;
  let navigate: jasmine.Spy;
  let loggedIn: boolean;

  beforeEach(() => {
    loggedIn = true;
    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        provideMockStore({ initialState: { game: initialGameState } }),
        provideRouter([]),
        { provide: AuthService, useValue: { get isLoggedIn() { return loggedIn; } } },
      ],
    });
    store = TestBed.inject(MockStore);
    dispatch = spyOn(store, 'dispatch');
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(HomePage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    dispatch.calls.reset();
  });

  it('starts a game against the AI with the chosen settings', () => {
    page.selectMode('ai');
    page.selectDifficulty(3);
    page.selectColor('random');

    page.startGame();

    const created = dispatch.calls.mostRecent().args[0];
    expect(created.type).toBe(GameActions.createGame.type);
    expect(created.options.mode).toBe('ai');
    expect(created.options.aiDifficulty).toBe(3);
    expect(created.options.playerColor).toBe('random');
  });

  it('sends someone who is not signed in to the login page, and back here afterwards', () => {
    loggedIn = false;

    page.startGame();

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/home' } });
    expect(dispatch).not.toHaveBeenCalledWith(jasmine.objectContaining({ type: GameActions.createGame.type }));
  });

  it('goes to the lobby for an online game', () => {
    page.selectMode('online');

    page.startGame();

    expect(navigate).toHaveBeenCalledWith(['/online']);
  });

  it('creates nothing in saved-game mode: a game is picked from the list instead', () => {
    page.selectMode('saved');

    page.startGame();

    expect(dispatch).not.toHaveBeenCalled();
    expect(page.canStart).toBeFalse();
  });

  it('can start in every other mode', () => {
    for (const mode of ['ai', 'local', 'online'] as const) {
      page.selectMode(mode);
      expect(page.canStart).withContext(mode).toBeTrue();
    }
  });

  describe('saved games', () => {
    const saved = { id: 's1', gameId: 'g1', opponentName: 'IA · Expert', mode: 'ai', turnNumber: 14, playerColor: 'white', savedAt: new Date() } as SavedGame;
    let studio: boolean;

    beforeEach(() => {
      studio = true;
      spyOn(window, 'matchMedia').and.callFake((query: string) => ({ matches: query.includes('1181') ? studio : false }) as MediaQueryList);
      page.selectMode('saved');
    });

    it('only picks a game where the launch bar is, and opens it from there', () => {
      page.pickSaved(saved);

      expect(dispatch).not.toHaveBeenCalled();
      expect(page.canStart).toBeTrue();
      expect(page.summary).toBe('vs IA · Expert · Tour 14');
      expect(page.ctaLabel).toBe('Reprendre la partie');

      page.startGame();

      expect(dispatch).toHaveBeenCalledWith(GameActions.loadGame({ gameId: 'g1' }));
    });

    it('opens the game at once where there is no launch bar', () => {
      studio = false;

      page.pickSaved(saved);

      expect(dispatch).toHaveBeenCalledWith(GameActions.loadGame({ gameId: 'g1' }));
    });
  });

  it('names what the start button does in each mode', () => {
    const labels = (['ai', 'local', 'online', 'saved'] as const).map((mode) => {
      page.selectMode(mode);
      return page.ctaLabel;
    });

    expect(labels).toEqual(['Lancer la partie', 'Lancer la partie', 'Ouvrir le salon', 'Reprendre la partie']);
  });

  it('shows why a game could not be created', () => {
    store.setState({ game: { ...initialGameState, notice: 'timeControl.type: must be blitz, rapid, classical or unlimited' } });
    fixture.detectChanges();

    const message = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');

    expect(message?.textContent).toContain('must be blitz');
  });

  it('opens the saved games at their own address, the one the "Parties" tab links to', () => {
    page.pickMode('saved');

    expect(navigate).toHaveBeenCalledWith(['/home'], { queryParams: { mode: 'saved' } });
  });

  it('sums up the chosen settings above the start button', () => {
    page.selectMode('ai');
    page.selectDifficulty(5);
    page.selectColor('black');
    page.selectTime('rapid');

    expect(page.summary).toBe('Maître (≈ 2550) · Noirs · Rapide 10′');
  });
});

describe('resumeCard', () => {
  const me = { id: 'me', username: 'me' } as User;
  const bot = { username: 'AI', color: 'black' as const, timeRemainingMs: 0, capturedPieces: [], isAi: true, aiDifficulty: 5 as const };

  it('offers to resume a game that is still going', () => {
    const card = resumeCard(makeGame({ id: 'g1', playerBlack: bot, fullMoveNumber: 9 }), me);

    expect(card).toEqual(jasmine.objectContaining({ id: 'g1', title: 'vs IA · Maître', sub: 'Tour 9 · à vous de jouer' }));
  });

  it('says when it is the opponent who is to move', () => {
    const card = resumeCard(makeGame({ playerBlack: bot, currentTurn: 'black' }), me);

    expect(card?.sub).toContain("à l'adversaire");
  });

  it('names the online opponent, and says whose turn it is in a local game', () => {
    const online = makeGame({
      mode: 'online',
      playerWhite: { username: 'me', color: 'white', timeRemainingMs: 0, capturedPieces: [], userId: 'me' },
      playerBlack: { username: 'marco', color: 'black', timeRemainingMs: 0, capturedPieces: [], userId: 'marco' },
    });
    expect(resumeCard(online, me)?.title).toBe('vs marco');

    const local = resumeCard(makeGame({ mode: 'local', currentTurn: 'black' }), me);
    expect(local?.title).toBe('Partie locale');
    expect(local?.sub).toContain('aux Noirs');
  });

  it('has nothing to offer without a game, or once it is over', () => {
    expect(resumeCard(null, me)).toBeNull();
    expect(resumeCard(makeGame({ status: 'checkmate' }), me)).toBeNull();
  });
});
