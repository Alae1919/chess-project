import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { combineLatest } from 'rxjs';
import { LobbyActions } from '../../../store/lobby/lobby.actions';
import {
  selectIsSearching,
  selectQueueEntry,
  selectSentInvitation,
  selectLobbyError,
} from '../../../store/lobby/lobby.selectors';
import { UserSearchComponent } from '../components/user-search.component';
import { UserSummary } from '../../../core/models';

type Tab = 'random' | 'friend';
type TimeOption = { label: string; type: string; initialMs: number; incrementMs: number };

@Component({
  selector: 'app-online-lobby-page',
  standalone: true,
  imports: [CommonModule, UserSearchComponent],
  template: `
    <div class="lobby-page" *ngIf="vm$ | async as vm">
      <!-- Tab switcher -->
      <div class="tabs">
        <button
          class="tab"
          [class.active]="activeTab === 'random'"
          (click)="activeTab = 'random'"
        >Play vs Random</button>
        <button
          class="tab"
          [class.active]="activeTab === 'friend'"
          (click)="activeTab = 'friend'"
        >Play vs Friend</button>
      </div>

      <!-- ── Random matchmaking panel ─────────────────────────────────────── -->
      <div class="panel" *ngIf="activeTab === 'random'">
        <h2 class="panel-title">Find an Opponent</h2>

        <div class="time-options">
          <button
            *ngFor="let opt of timeOptions"
            class="time-btn"
            [class.selected]="selectedTime === opt"
            (click)="selectedTime = opt"
          >
            <span class="time-type">{{ opt.type | titlecase }}</span>
            <span class="time-label">{{ opt.label }}</span>
          </button>
        </div>

        <div class="queue-status" *ngIf="vm.isSearching">
          <div class="spinner"></div>
          <p>Searching for an opponent…</p>
          <p class="queue-hint" *ngIf="vm.queueEntry">
            In queue since {{ formatTime(vm.queueEntry.joinedAt) }}
          </p>
        </div>

        <div class="actions" *ngIf="!vm.isSearching">
          <button class="btn-primary" (click)="joinQueue()">
            Play Now
          </button>
        </div>

        <div class="actions" *ngIf="vm.isSearching">
          <button class="btn-cancel" (click)="leaveQueue()">
            Cancel Search
          </button>
        </div>

        <p class="error" *ngIf="vm.error">{{ vm.error }}</p>
      </div>

      <!-- ── Friend challenge panel ───────────────────────────────────────── -->
      <div class="panel" *ngIf="activeTab === 'friend'">
        <h2 class="panel-title">Challenge a Friend</h2>

        <div class="friend-search-section">
          <label class="field-label">Search player</label>
          <app-user-search (userSelected)="selectFriend($event)" />
          <div class="selected-friend" *ngIf="selectedFriend">
            <span class="friend-name">{{ selectedFriend.username }}</span>
            <span class="friend-elo">ELO {{ selectedFriend.elo }}</span>
          </div>
        </div>

        <div class="time-options">
          <button
            *ngFor="let opt of timeOptions"
            class="time-btn"
            [class.selected]="selectedTime === opt"
            (click)="selectedTime = opt"
          >
            <span class="time-type">{{ opt.type | titlecase }}</span>
            <span class="time-label">{{ opt.label }}</span>
          </button>
        </div>

        <!-- Pending sent invitation -->
        <div class="pending-invite" *ngIf="vm.sentInvitation">
          <div class="spinner small"></div>
          <p>Waiting for <strong>{{ vm.sentInvitation.inviteeUsername }}</strong> to respond…</p>
          <button class="btn-cancel" (click)="cancelInvitation(vm.sentInvitation!.invitationId)">
            Cancel
          </button>
        </div>

        <div class="actions" *ngIf="!vm.sentInvitation">
          <button
            class="btn-primary"
            [disabled]="!selectedFriend"
            (click)="sendInvitation()"
          >
            Send Challenge
          </button>
        </div>

        <p class="error" *ngIf="vm.error">{{ vm.error }}</p>
      </div>
    </div>
  `,
  styles: [`
    .lobby-page {
      max-width: 520px; margin: 60px auto; padding: 0 16px;
      font-family: 'Inter', sans-serif;
    }
    .tabs {
      display: flex; gap: 0; background: #1a1e2e;
      border-radius: 10px; padding: 4px; margin-bottom: 24px;
    }
    .tab {
      flex: 1; padding: 10px; background: none; border: none;
      border-radius: 8px; color: #8892a4; font-size: 14px; cursor: pointer;
      transition: all 0.2s;
    }
    .tab.active { background: #252a3d; color: #e0e6f0; font-weight: 600; }
    .panel {
      background: #1e2130; border-radius: 14px; padding: 28px;
      border: 1px solid #2a2f42;
    }
    .panel-title { color: #e0e6f0; font-size: 20px; font-weight: 700; margin: 0 0 20px; }
    .time-options { display: flex; gap: 10px; margin-bottom: 24px; }
    .time-btn {
      flex: 1; padding: 12px 8px; background: #252a3d; border: 1px solid #2a2f42;
      border-radius: 10px; cursor: pointer; color: #8892a4; transition: all 0.2s;
      display: flex; flex-direction: column; align-items: center; gap: 4px;
    }
    .time-btn.selected { border-color: #4a6fa5; color: #6ab0ff; background: #1e2a3d; }
    .time-type { font-size: 13px; font-weight: 600; }
    .time-label { font-size: 11px; }
    .queue-status { text-align: center; padding: 20px 0; color: #8892a4; }
    .queue-hint { font-size: 12px; margin-top: 8px; }
    .spinner {
      width: 36px; height: 36px; border: 3px solid #2a2f42;
      border-top-color: #4a6fa5; border-radius: 50%;
      animation: spin 0.8s linear infinite; margin: 0 auto 12px;
    }
    .spinner.small { width: 20px; height: 20px; border-width: 2px; display: inline-block; margin: 0 8px 0 0; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .actions { display: flex; justify-content: center; margin-top: 8px; }
    .btn-primary {
      padding: 12px 40px; background: #4a6fa5; color: #fff; border: none;
      border-radius: 10px; font-size: 15px; font-weight: 600; cursor: pointer;
      transition: background 0.2s;
    }
    .btn-primary:hover:not(:disabled) { background: #5a82c0; }
    .btn-primary:disabled { opacity: 0.5; cursor: default; }
    .btn-cancel {
      padding: 10px 28px; background: none; border: 1px solid #3a3f52;
      color: #8892a4; border-radius: 10px; font-size: 14px; cursor: pointer;
      transition: all 0.2s;
    }
    .btn-cancel:hover { background: #1a1e2e; color: #e0e6f0; }
    .field-label { display: block; font-size: 13px; color: #8892a4; margin-bottom: 8px; }
    .friend-search-section { margin-bottom: 20px; }
    .selected-friend {
      display: flex; align-items: center; gap: 10px; margin-top: 10px;
      padding: 8px 12px; background: #252a3d; border-radius: 8px;
    }
    .friend-name { color: #6ab0ff; font-weight: 600; font-size: 14px; }
    .friend-elo { color: #8892a4; font-size: 12px; }
    .pending-invite {
      display: flex; align-items: center; gap: 8px; color: #8892a4;
      font-size: 14px; margin-bottom: 16px;
    }
    .error { color: #f87171; font-size: 13px; margin-top: 12px; text-align: center; }
  `],
})
export class OnlineLobbyPage implements OnInit {
  private store = inject(Store);

  activeTab: Tab = 'random';
  selectedFriend: UserSummary | null = null;

  timeOptions: TimeOption[] = [
    { label: '5 min',  type: 'blitz',     initialMs: 5  * 60_000, incrementMs: 0 },
    { label: '10 min', type: 'rapid',     initialMs: 10 * 60_000, incrementMs: 0 },
    { label: '30 min', type: 'classical', initialMs: 30 * 60_000, incrementMs: 0 },
  ];
  selectedTime = this.timeOptions[1];

  vm$ = combineLatest({
    isSearching: this.store.select(selectIsSearching),
    queueEntry:  this.store.select(selectQueueEntry),
    sentInvitation: this.store.select(selectSentInvitation),
    error: this.store.select(selectLobbyError),
  });

  ngOnInit(): void {
    this.store.dispatch(LobbyActions.loadPendingInvitations());
  }

  joinQueue(): void {
    this.store.dispatch(LobbyActions.joinQueue({
      req: {
        timeControlType:   this.selectedTime.type,
        timeControlInitialMs: this.selectedTime.initialMs,
        timeControlIncrementMs: this.selectedTime.incrementMs,
      },
    }));
  }

  leaveQueue(): void {
    this.store.dispatch(LobbyActions.leaveQueue());
  }

  selectFriend(user: UserSummary): void {
    this.selectedFriend = user;
  }

  sendInvitation(): void {
    if (!this.selectedFriend) return;
    this.store.dispatch(LobbyActions.sendInvitation({
      req: {
        inviteeUsername: this.selectedFriend.username,
        timeControlType: this.selectedTime.type,
        timeControlInitialMs: this.selectedTime.initialMs,
        timeControlIncrementMs: this.selectedTime.incrementMs,
      },
    }));
  }

  cancelInvitation(id: string): void {
    this.store.dispatch(LobbyActions.cancelInvitation({ invitationId: id }));
  }

  formatTime(joinedAt: string): string {
    return new Date(joinedAt).toLocaleTimeString();
  }
}
