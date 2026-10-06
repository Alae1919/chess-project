import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { GameInvitation } from '../../../core/models';
import { LobbyActions } from '../../../store/lobby/lobby.actions';
import { initialLobbyState } from '../../../store/lobby/lobby.state';
import { InvitationToastComponent } from './invitation-toast.component';

describe('InvitationToastComponent', () => {
  let fixture: ComponentFixture<InvitationToastComponent>;
  let store: MockStore;
  let dispatch: jasmine.Spy;

  const invitation = (id: string, from: string): GameInvitation => ({
    invitationId: id, inviterUsername: from, inviteeUsername: 'me', status: 'pending',
    timeControlType: 'blitz', timeControlInitialMs: 300_000, timeControlIncrementMs: 0,
    createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 60_000).toISOString(),
  } as unknown as GameInvitation);

  const lobby = (respondingTo: string[] = []) => ({
    lobby: { ...initialLobbyState, pendingInvitations: [invitation('a', 'ann'), invitation('b', 'bob')], respondingTo },
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [InvitationToastComponent], providers: [provideMockStore({ initialState: lobby() })] });
    store = TestBed.inject(MockStore);
    dispatch = spyOn(store, 'dispatch');
    fixture = TestBed.createComponent(InvitationToastComponent);
    fixture.detectChanges();
  });

  const buttons = (label: string): HTMLButtonElement[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).filter((b) => b.textContent?.trim() === label);

  it('shows each invitation with an accept and a decline button', () => {
    expect(buttons('Accept').length).toBe(2);
    expect(buttons('Decline').length).toBe(2);
  });

  it('sends the answer for the invitation that was clicked', () => {
    buttons('Accept')[1].click();

    expect(dispatch).toHaveBeenCalledWith(LobbyActions.respondToInvitation({ invitationId: 'b', response: 'accept' }));
  });

  it('turns the buttons of an invitation off while its answer is on its way', () => {
    store.setState(lobby(['a']));
    fixture.detectChanges();

    expect(buttons('Accept')[0].disabled).toBeTrue();
    expect(buttons('Decline')[0].disabled).toBeTrue();
    expect(buttons('Accept')[1].disabled).toBeFalse();   // the other one is untouched
  });
});
