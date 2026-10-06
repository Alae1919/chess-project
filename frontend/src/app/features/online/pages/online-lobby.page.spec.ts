import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { LobbyActions } from '../../../store/lobby/lobby.actions';
import { initialLobbyState } from '../../../store/lobby/lobby.state';
import { UserSearchComponent } from '../components/user-search.component';
import { OnlineLobbyPage } from './online-lobby.page';

describe('OnlineLobbyPage', () => {
  let fixture: ComponentFixture<OnlineLobbyPage>;
  let store: MockStore;
  let dispatch: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OnlineLobbyPage],
      providers: [provideMockStore({ initialState: { lobby: initialLobbyState } })],
    });
    TestBed.overrideComponent(OnlineLobbyPage, {
      remove: { imports: [UserSearchComponent] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    store = TestBed.inject(MockStore);
    dispatch = spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(OnlineLobbyPage);
    fixture.detectChanges();
  });

  it('forgets an error from an earlier visit', () => {
    expect(dispatch).toHaveBeenCalledWith(LobbyActions.clearError());
  });

  it('takes the player out of the queue when they leave the page while searching', () => {
    store.setState({ lobby: { ...initialLobbyState, isSearching: true } });
    dispatch.calls.reset();

    fixture.destroy();

    expect(dispatch).toHaveBeenCalledWith(LobbyActions.leaveQueue());
  });

  it('does nothing on the way out when they were not searching', () => {
    dispatch.calls.reset();

    fixture.destroy();

    expect(dispatch).not.toHaveBeenCalledWith(LobbyActions.leaveQueue());
  });
});
