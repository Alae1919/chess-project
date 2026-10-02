import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GameOverDialogComponent } from './game-over-dialog.component';

describe('GameOverDialogComponent', () => {
  let fixture: ComponentFixture<GameOverDialogComponent>;
  let component: GameOverDialogComponent;
  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent!.replace(/\s+/g, ' ');

  beforeEach(() => {
    fixture = TestBed.createComponent(GameOverDialogComponent);
    component = fixture.componentInstance;
    component.summary = { headline: 'Victoire', reason: 'Échec et mat', tone: 'win', eloChange: 16 };
    fixture.detectChanges();
  });

  it('shows the result, how it ended and the rating change', () => {
    expect(text()).toContain('Victoire');
    expect(text()).toContain('Échec et mat');
    expect(text()).toContain('+16 Elo');
  });

  it('shows a loss as a minus', () => {
    component.summary = { headline: 'Défaite', reason: 'Abandon', tone: 'loss', eloChange: -12 };
    fixture.detectChanges();

    expect(text()).toContain('−12 Elo');
  });

  it('has no rating line for unrated games', () => {
    component.summary = { headline: 'Match nul', reason: 'Pat', tone: 'draw', eloChange: null };
    fixture.detectChanges();

    expect(text()).not.toContain('Elo');
  });

  it('only offers the lobby in online games', () => {
    expect(text()).not.toContain('Salon');
    component.online = true;
    fixture.detectChanges();

    expect(text()).toContain('Salon');
  });

  it('turns the rematch button into a waiting note while the invitation is out', () => {
    component.rematch = 'pending';
    fixture.detectChanges();

    const rematch = el().querySelector<HTMLButtonElement>('.btn--primary')!;
    expect(rematch.textContent).toContain('Revanche proposée');
    expect(rematch.disabled).toBeTrue();
  });

  it('says so when the rematch was not accepted', () => {
    component.rematch = 'declined';
    fixture.detectChanges();

    expect(text()).toContain("n'a pas accepté la revanche");
  });

  it('emits for each choice, and closes on Escape', () => {
    const events: string[] = [];
    component.rematchClick.subscribe(() => events.push('rematch'));
    component.newGame.subscribe(() => events.push('new'));
    component.close.subscribe(() => events.push('close'));

    const buttons = Array.from(el().querySelectorAll<HTMLButtonElement>('.btn'));
    buttons[0].click();
    buttons[1].click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(events).toEqual(['rematch', 'new', 'close']);
  });
});
