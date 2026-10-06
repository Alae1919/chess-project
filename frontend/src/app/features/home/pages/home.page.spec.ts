import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { FloatyPiecesComponent } from '../../../shared/components/floaty-pieces/floaty-pieces.component';
import { GameActions } from '../../../store/game/game.actions';
import { initialGameState } from '../../../store/game/game.state';
import { HomePage } from './home.page';

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
        { provide: BoardPrefsService, useValue: { prefs$: of({ style3d: 'marble-gold' }) } },
      ],
    });
    TestBed.overrideComponent(HomePage, {
      remove: { imports: [ChessBoard3DComponent, FloatyPiecesComponent] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
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

  it('shows why a game could not be created', () => {
    store.setState({ game: { ...initialGameState, notice: 'timeControl.type: must be blitz, rapid, classical or unlimited' } });
    fixture.detectChanges();

    const message = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');

    expect(message?.textContent).toContain('must be blitz');
  });
});
