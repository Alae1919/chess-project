import {
  AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ResultSummary } from '../../../core/utils/game-result.utils';
import { SheetDragDirective } from '../../directives/sheet-drag.directive';

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
  imports: [CommonModule, SheetDragDirective],
  template: `
    <div class="backdrop" (click)="close.emit()"></div>
    <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="over-title" #dialog
         appSheetDrag (dismiss)="close.emit()">
      <span class="sheet__grab grab"></span>
      <button type="button" class="x" aria-label="Fermer" (click)="close.emit()">×</button>

      <div class="top">
        <div>
          <p class="kicker"><span class="d-only">Partie terminée</span><span class="m-only">{{ summary.reason }}</span></p>
          <h2 id="over-title" class="headline" [ngClass]="'tone-' + summary.tone">{{ summary.headline }}</h2>
          <p class="reason d-only">{{ summary.reason }}</p>
        </div>
        <div class="medal m-only" [ngClass]="'medal--' + summary.tone" aria-hidden="true">{{ summary.tone === 'draw' ? '½' : '♚' }}</div>
      </div>

      <dl class="stats m-only" *ngIf="moves !== null">
        <div><dt>coups</dt><dd>{{ moves }}</dd></div>
        <div><dt>cadence</dt><dd>{{ clock }}</dd></div>
        <div><dt>adversaire</dt><dd>{{ opponent }}</dd></div>
      </dl>

      <p class="elo" *ngIf="summary.eloChange !== null"
         [class.elo--up]="summary.eloChange > 0" [class.elo--down]="summary.eloChange < 0">
        {{ summary.eloChange > 0 ? '+' : summary.eloChange < 0 ? '−' : '±' }}{{ absChange() }} Elo
      </p>

      <div class="actions">
        <button type="button" class="btn btn--primary" [disabled]="rematch === 'pending'" (click)="rematchClick.emit()">
          {{ rematch === 'pending' ? 'Revanche proposée…' : '' }}<ng-container *ngIf="rematch !== 'pending'"><span class="d-only">Revanche</span><span class="m-only">Rejouer</span></ng-container>
        </button>
        <button type="button" class="btn" (click)="newGame.emit()"><span class="d-only">Nouvelle partie</span><span class="m-only">Accueil</span></button>
        <button type="button" class="btn d-only" *ngIf="online" (click)="lobby.emit()">Salon</button>
        <button type="button" class="btn-link m-only" (click)="close.emit()">Voir le plateau</button>
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
    .grab, .m-only { display: none; }
    .top { display: block; }
    .stats { display: none; }

    /* Phones: a sheet from the bottom, the final position left in view above it */
    @media (max-width: 768px) {
      :host { place-items: end stretch; padding: 0; }
      .backdrop { background: linear-gradient(180deg, transparent 30%, rgba(8, 6, 4, .72) 62%); backdrop-filter: none; }
      .dialog {
        width: 100%; padding: 10px 20px calc(26px + env(safe-area-inset-bottom)); text-align: left;
        border: 0; border-top: 1px solid rgba(230, 194, 122, .5); border-radius: 26px 26px 0 0;
        background: linear-gradient(180deg, #211a13 0%, #120e0a 100%);
        box-shadow: 0 -20px 60px rgba(0, 0, 0, .6), 0 -1px 40px rgba(230, 194, 122, .12);
        animation: sheet-up .42s var(--ease-spring) both;
      }
      .grab { display: block; }
      .x { display: none; }   /* "Voir le plateau" closes it */
      .d-only { display: none; }
      .m-only { display: inline; }
      .top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
      .medal {
        width: 68px; height: 68px; flex-shrink: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center;
        font: 36px 'Segoe UI Symbol', 'Noto Sans Symbols 2', serif; color: #140f08;
        background: var(--gold-grad); box-shadow: 0 0 0 6px rgba(230, 194, 122, .12), 0 0 40px rgba(230, 194, 122, .35);
      }
      .medal--loss { color: var(--textd); background: #1b1510; box-shadow: 0 0 0 1px rgba(201, 164, 92, .3); }
      .medal--draw { font-family: var(--serif); font-weight: 600; background: #2a2118; color: var(--gold); box-shadow: 0 0 0 1px rgba(201, 164, 92, .4); }
      .stats {
        display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 16px 0 0;
        background: rgba(255, 255, 255, .035); border: 1px solid rgba(201, 164, 92, .14); border-radius: 14px;
      }
      .stats div { padding: 12px 4px; display: flex; flex-direction: column-reverse; align-items: center; gap: 2px; }
      .stats div + div { border-left: 1px solid rgba(201, 164, 92, .14); }
      .stats dd { margin: 0; font: 600 20px var(--serif); color: var(--text); white-space: nowrap; max-width: 100%; overflow: hidden; text-overflow: ellipsis; }
      .stats dt { font-size: 11px; letter-spacing: .12em; text-transform: uppercase; color: var(--textc); }
      .btn-link {
        grid-column: 1 / -1; height: 44px; background: none; border: 0; cursor: pointer;
        font: 400 14px var(--font); color: var(--gold);
      }
      .x { top: 14px; right: 10px; width: 44px; height: 44px; }
      .kicker { margin: 10px 0 0; font-size: 11px; letter-spacing: .26em; color: var(--p); }
      .headline { font: italic 500 52px/1.05 var(--serif); }
      .tone-win {
        background: linear-gradient(135deg, #f8e7b8 0%, #e6c27a 45%, #a9823c 100%);
        -webkit-background-clip: text; background-clip: text; color: transparent;
      }
      .reason { font-size: 15px; }
      .actions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 20px; }
      .btn { height: 54px; border-radius: 14px; font-size: 13px; letter-spacing: .12em; text-transform: uppercase; border-color: rgba(201, 164, 92, .45); }
      .btn--primary {
        grid-column: 1 / -1; border: 0; color: #140f08; background: var(--gold-grad);
        box-shadow: 0 10px 30px rgba(201, 164, 92, .25), inset 0 1px 0 rgba(255, 255, 255, .35);
      }
      .actions .btn:nth-child(2) { grid-column: 1 / -1; }
    }
  `],
})
export class GameOverDialogComponent implements AfterViewInit {
  @Input({ required: true }) summary!: ResultSummary;
  /** Online games get a "Salon" button */
  @Input() online = false;
  @Input() rematch: RematchState = 'idle';
  /** For the phone sheet's summary: moves played, the clock, the opponent (null: no summary) */
  @Input() moves: number | null = null;
  @Input() clock = '';
  @Input() opponent = '';

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
