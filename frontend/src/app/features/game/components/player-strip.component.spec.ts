import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GamePlayer } from '../../../core/models';
import { PlayerStripComponent } from './player-strip.component';

describe('PlayerStripComponent', () => {
  let fixture: ComponentFixture<PlayerStripComponent>;

  function show(player: Partial<GamePlayer>, active = false) {
    fixture = TestBed.createComponent(PlayerStripComponent);
    fixture.componentRef.setInput('player', { username: 'alice', color: 'white', timeRemainingMs: 452_000, ...player });
    fixture.componentRef.setInput('active', active);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the player and their clock even when the server sent no captured pieces', () => {
    const el = show({ capturedPieces: undefined as never });

    expect(el.textContent).toContain('alice');
    expect(el.querySelector('.clock')?.textContent).toContain('07:32');
  });

  it('names an AI by its level', () => {
    const el = show({ isAi: true, aiDifficulty: 5, capturedPieces: [] });

    expect(el.textContent).toContain('IA · Maître');
    expect(el.textContent).toContain('≈ 2550');
  });

  it('calls the person holding the phone "Vous", and everyone else by name', () => {
    fixture = TestBed.createComponent(PlayerStripComponent);
    fixture.componentRef.setInput('player', { username: 'alice', color: 'white', timeRemainingMs: 1000, capturedPieces: [] });
    fixture.componentRef.setInput('me', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.name').textContent).toContain('Vous');

    fixture.componentRef.setInput('me', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.name').textContent).toContain('alice');
  });

  it('runs the clock on the player to move, and warns under 30 seconds', () => {
    const el = show({ capturedPieces: [], timeRemainingMs: 12_000 }, true);

    expect(el.querySelector('.clock--running')).not.toBeNull();
    expect(el.querySelector('.clock--low')).not.toBeNull();
  });
});
