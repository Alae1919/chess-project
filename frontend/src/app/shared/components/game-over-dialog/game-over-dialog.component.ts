import {
  AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ResultSummary } from '../../../core/utils/game-result.utils';

/** Where an online rematch invitation stands. */
export type RematchState = 'idle' | 'pending' | 'declined';

/**
 * Shown over the board when a game ends: the result and how it came about, the
 * rating change, and what to do next. Escape or the cross closes it to look at
 * the final position.
 */
@Component({
  selector: 'app-game-over-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="backdrop" (click)="close.emit()"></div>
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="over-title" #dialog>
      <button type="button" class="x" aria-label="Fermer" (click)="close.emit()">×</button>

      <p class="kicker">Partie terminée</p>
      <h2 id="over-title" class="headline" [ngClass]="'tone-' + summary.tone">{{ summary.headline }}</h2>
      <p class="reason">{{ summary.reason }}</p>

      <p class="elo" *ngIf="summary.eloChange !== null"
         [class.elo--up]="summary.eloChange > 0" [class.elo--down]="summary.eloChange < 0">
        {{ summary.eloChange > 0 ? '+' : summary.eloChange < 0 ? '−' : '±' }}{{ absChange() }} Elo
      </p>

      <div class="actions">
        <button type="button" class="btn btn--primary" [disabled]="rematch === 'pending'" (click)="rematchClick.emit()">
          {{ rematch === 'pending' ? 'Revanche proposée…' : 'Revanche' }}
        </button>
        <button type="button" class="btn" (click)="newGame.emit()">Nouvelle partie</button>
        <button type="button" class="btn" *ngIf="online" (click)="lobby.emit()">Salon</button>
      </div>

      <p class="note" *ngIf="rematch === 'declined'" role="status">L'adversaire n'a pas accepté la revanche.</p>
    </div>
  `,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 50; display: grid; place-items: center; padding: 16px; }
    .backdrop { position: absolute; inset: 0; background: rgba(8, 6, 4, .66); backdrop-filter: blur(3px); }
    .dialog {
      position: relative; width: min(100%, 380px); padding: 28px 26px 24px; text-align: center;
      background: var(--surface); border: 1px solid var(--borderh); border-radius: var(--radius-lg);
      box-shadow: var(--pg), 0 24px 64px rgba(0, 0, 0, .65);
    }
    .x {
      position: absolute; top: 8px; right: 12px; background: none; border: 0; cursor: pointer;
      color: var(--textd); font-size: 24px; line-height: 1; padding: 4px 8px;
    }
    .x:hover, .x:focus-visible { color: var(--text); outline: none; }
    .kicker { margin: 0 0 6px; font: 500 11px var(--font); color: var(--textd); text-transform: uppercase; letter-spacing: .14em; }
    .headline { margin: 0; font: 600 2.1rem var(--serif); color: var(--text); }
    .tone-win { color: var(--gold); }
    .tone-loss { color: #d9806f; }
    .reason { margin: 6px 0 0; color: var(--textd); font: 400 .95rem var(--font); }
    .elo { margin: 14px 0 0; font: 600 1.05rem var(--font); font-variant-numeric: tabular-nums; color: var(--textd); }
    .elo--up { color: #8fcf8f; }
    .elo--down { color: #d9806f; }
    .actions { display: flex; flex-direction: column; gap: 9px; margin-top: 22px; }
    .btn {
      padding: 11px 16px; border-radius: var(--radius-md); cursor: pointer; font: 500 .9rem var(--font);
      color: var(--text); background: transparent; border: 1px solid var(--border); transition: border-color .2s, background .2s;
    }
    .btn:hover:not(:disabled), .btn:focus-visible { border-color: var(--p); outline: none; }
    .btn:disabled { opacity: .6; cursor: default; }
    .btn--primary { background: var(--pd); border-color: var(--p); color: var(--gold); }
    .note { margin: 14px 0 0; color: var(--textd); font: 400 .82rem var(--font); }
  `],
})
export class GameOverDialogComponent implements AfterViewInit {
  @Input({ required: true }) summary!: ResultSummary;
  /** Online games get a "Salon" button */
  @Input() online = false;
  @Input() rematch: RematchState = 'idle';

  @Output() rematchClick = new EventEmitter<void>();
  @Output() newGame = new EventEmitter<void>();
  @Output() lobby = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  @ViewChild('dialog') private dialog?: ElementRef<HTMLElement>;

  absChange(): number {
    return Math.abs(this.summary.eloChange ?? 0);
  }

  ngAfterViewInit(): void {
    this.dialog?.nativeElement.querySelector<HTMLButtonElement>('.btn--primary')?.focus();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close.emit();
  }
}
