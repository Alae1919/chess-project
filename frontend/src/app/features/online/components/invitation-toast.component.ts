import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { interval, Subscription } from 'rxjs';
import { selectPendingInvitations } from '../../../store/lobby/lobby.selectors';
import { LobbyActions } from '../../../store/lobby/lobby.actions';
import { GameInvitation } from '../../../core/models';

@Component({
  selector: 'app-invitation-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="invitation-toasts" *ngIf="invitations.length > 0">
      <div class="invitation-toast" *ngFor="let inv of invitations">
        <div class="toast-header">
          <span class="challenger">{{ inv.inviterUsername }}</span>
          <span class="label"> challenges you!</span>
        </div>
        <div class="toast-details">
          {{ formatTimeControl(inv.timeControlType, inv.timeControlInitialMs) }}
        </div>
        <div class="toast-timer" [class.urgent]="getSecondsLeft(inv) < 30">
          Expires in {{ getSecondsLeft(inv) }}s
        </div>
        <div class="toast-actions">
          <button class="btn-accept" (click)="accept(inv)">Accept</button>
          <button class="btn-decline" (click)="decline(inv)">Decline</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .invitation-toasts {
      position: fixed; bottom: 24px; right: 24px; z-index: 9999;
      display: flex; flex-direction: column; gap: 12px;
    }
    .invitation-toast {
      background: #252a3d; border: 1px solid #4a6fa5; border-radius: 12px;
      padding: 16px 20px; width: 280px; box-shadow: 0 8px 32px rgba(0,0,0,0.4);
    }
    .toast-header { font-size: 14px; color: #e0e6f0; margin-bottom: 6px; }
    .challenger { font-weight: 700; color: #6ab0ff; }
    .label { color: #c0c8d8; }
    .toast-details { font-size: 13px; color: #8892a4; margin-bottom: 4px; }
    .toast-timer { font-size: 12px; color: #8892a4; margin-bottom: 12px; }
    .toast-timer.urgent { color: #f87171; }
    .toast-actions { display: flex; gap: 8px; }
    .btn-accept {
      flex: 1; padding: 8px; border-radius: 6px; border: none;
      background: #4a6fa5; color: #fff; font-size: 13px; cursor: pointer;
      transition: background 0.15s;
    }
    .btn-accept:hover { background: #5a82c0; }
    .btn-decline {
      flex: 1; padding: 8px; border-radius: 6px; border: 1px solid #3a3f52;
      background: transparent; color: #8892a4; font-size: 13px; cursor: pointer;
      transition: background 0.15s;
    }
    .btn-decline:hover { background: #1e2130; }
  `],
})
export class InvitationToastComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private timerSub?: Subscription;

  invitations: GameInvitation[] = [];
  now = Date.now();

  ngOnInit(): void {
    this.store.select(selectPendingInvitations).subscribe((inv) => {
      this.invitations = inv.filter((i) => new Date(i.expiresAt).getTime() > Date.now());
    });
    this.timerSub = interval(1000).subscribe(() => {
      this.now = Date.now();
      // Remove expired toasts from display
      this.invitations = this.invitations.filter(
        (i) => new Date(i.expiresAt).getTime() > this.now
      );
    });
  }

  ngOnDestroy(): void {
    this.timerSub?.unsubscribe();
  }

  getSecondsLeft(inv: GameInvitation): number {
    return Math.max(0, Math.round((new Date(inv.expiresAt).getTime() - this.now) / 1000));
  }

  formatTimeControl(type: string, initialMs: number): string {
    const minutes = Math.round(initialMs / 60_000);
    return `${type.charAt(0).toUpperCase() + type.slice(1)} · ${minutes}min`;
  }

  accept(inv: GameInvitation): void {
    this.store.dispatch(LobbyActions.respondToInvitation({
      invitationId: inv.invitationId,
      response: 'accept',
    }));
  }

  decline(inv: GameInvitation): void {
    this.store.dispatch(LobbyActions.respondToInvitation({
      invitationId: inv.invitationId,
      response: 'decline',
    }));
  }
}
