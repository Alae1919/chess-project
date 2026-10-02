import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Shows an open draw offer under the board: Accept / Decline when the opponent
 * made it, a waiting note when you did.
 */
@Component({
  selector: 'app-draw-offer-banner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="banner" role="status" aria-live="polite" *ngIf="kind === 'incoming'; else waiting">
      <span>Votre adversaire propose la nulle</span>
      <button type="button" class="btn accept" (click)="accept.emit()">Accepter</button>
      <button type="button" class="btn decline" (click)="decline.emit()">Refuser</button>
    </div>
    <ng-template #waiting>
      <div class="banner banner--waiting" role="status" aria-live="polite">Nulle proposée — en attente de réponse</div>
    </ng-template>
  `,
  styles: [`
    .banner {
      margin-top: 14px; padding: 8px 14px; border-radius: 20px; display: flex; align-items: center;
      gap: 10px; flex-wrap: wrap; justify-content: center;
      background: var(--bg2); border: 1px solid var(--borderh); color: var(--text);
      font: 500 .8rem var(--font); box-shadow: 0 4px 20px rgba(201, 164, 92, .2);
    }
    .banner--waiting { color: var(--textd); box-shadow: none; }
    .btn {
      padding: 5px 14px; border-radius: 14px; cursor: pointer; font: 500 .78rem var(--font);
      border: 1px solid var(--border); background: transparent; color: var(--text);
      transition: border-color .2s, background .2s;
    }
    .btn:hover, .btn:focus-visible { border-color: var(--p); outline: none; }
    .accept { background: var(--pd); border-color: var(--p); color: var(--gold); }
  `],
})
export class DrawOfferBannerComponent {
  @Input({ required: true }) kind!: 'incoming' | 'outgoing';
  @Output() accept = new EventEmitter<void>();
  @Output() decline = new EventEmitter<void>();
}
