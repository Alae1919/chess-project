import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { combineLatest, map, Subscription, timer } from 'rxjs';
import { LobbyActions } from '../../../store/lobby/lobby.actions';
import {
  selectIsSearching,
  selectQueueEntry,
  selectSentInvitation,
  selectLobbyError,
} from '../../../store/lobby/lobby.selectors';
import { selectUser } from '../../../store/account/account.reducer';
import { UserSearchComponent } from '../components/user-search.component';
import { UserSummary } from '../../../core/models';

type Tab = 'random' | 'friend';
type TimeOption = { label: string; name: string; type: string; initialMs: number; incrementMs: number };

/** "0:07", "12:30": how long since the given moment */
export function elapsedSince(iso: string | undefined, now = Date.now()): string {
  if (!iso) return '0:00';
  const total = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

@Component({
  selector: 'app-online-lobby-page',
  standalone: true,
  imports: [CommonModule, UserSearchComponent],
  template: `
    <div class="lobby" *ngIf="vm$ | async as vm">
      <header class="lobby__head">
        <p class="lobby__eyebrow">Multijoueur</p>
        <h1 class="lobby__title">En <em>ligne</em></h1>
        <p class="lobby__sub">Classé ou amical — affrontez un adversaire en temps réel.</p>
      </header>

      <div class="seg" role="tablist" aria-label="Type de partie">
        <button type="button" role="tab" class="seg__btn" [class.seg__btn--on]="activeTab === 'random'"
                [attr.aria-selected]="activeTab === 'random'" (click)="activeTab = 'random'">Partie rapide</button>
        <button type="button" role="tab" class="seg__btn" [class.seg__btn--on]="activeTab === 'friend'"
                [attr.aria-selected]="activeTab === 'friend'" (click)="activeTab = 'friend'">Défier un ami</button>
      </div>

      <!-- ── Random matchmaking ─────────────────────────────────────────── -->
      <section class="pane" *ngIf="activeTab === 'random'" role="tabpanel">
        <ng-container *ngIf="!vm.isSearching; else searching">
          <p class="label">Cadence</p>
          <div class="times">
            <button type="button" *ngFor="let opt of timeOptions" class="time"
                    [class.time--on]="selectedTime === opt" [attr.aria-pressed]="selectedTime === opt"
                    (click)="selectedTime = opt">
              <span class="time__mins">{{ opt.label }}</span>
              <span class="time__name">{{ opt.name }}</span>
            </button>
          </div>

          <div class="elo-row" *ngIf="vm.user as user">
            <span>Votre Elo</span>
            <strong>{{ user.elo }}</strong>
          </div>

          <div class="pane__foot">
            <button type="button" class="cta-block" (click)="joinQueue()">Trouver un adversaire</button>
          </div>
        </ng-container>

        <ng-template #searching>
          <div class="search">
            <div class="radar" aria-hidden="true">
              <span class="radar__ring"></span>
              <span class="radar__ring"></span>
              <span class="radar__ring"></span>
              <span class="radar__sweep"></span>
              <span class="radar__inner"></span>
              <span class="radar__core">♞</span>
            </div>
            <h2 class="search__title" role="status">Recherche d'un adversaire…</h2>
            <p class="search__sub">{{ selectedTime.name }} {{ selectedTime.label }} · en file depuis {{ vm.waited }}</p>
            <button type="button" class="btn-g search__cancel" (click)="leaveQueue()">Annuler la recherche</button>
          </div>
        </ng-template>

        <p class="error" role="alert" *ngIf="vm.error">{{ vm.error }}</p>
      </section>

      <!-- ── Friend challenge ───────────────────────────────────────────── -->
      <section class="pane" *ngIf="activeTab === 'friend'" role="tabpanel">
        <label class="label" for="friend-search">Rechercher un joueur</label>
        <app-user-search inputId="friend-search" (userSelected)="selectFriend($event)" />

        <div class="picked" *ngIf="selectedFriend">
          <span class="picked__avatar">{{ selectedFriend.username.slice(0, 2).toUpperCase() }}</span>
          <span class="picked__info">
            <span class="picked__name">{{ selectedFriend.username }}</span>
            <span class="picked__elo">Elo {{ selectedFriend.elo }}</span>
          </span>
          <svg class="picked__check" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>
        </div>

        <p class="label">Cadence</p>
        <div class="times times--compact">
          <button type="button" *ngFor="let opt of timeOptions" class="time"
                  [class.time--on]="selectedTime === opt" [attr.aria-pressed]="selectedTime === opt"
                  (click)="selectedTime = opt">
            <span class="time__mins">{{ opt.label }}</span>
            <span class="time__name">{{ opt.name }}</span>
          </button>
        </div>

        <div class="pending" *ngIf="vm.sentInvitation" role="status">
          <span class="pending__pulse" aria-hidden="true"></span>
          <p>En attente de <strong>{{ vm.sentInvitation.inviteeUsername }}</strong>…</p>
          <button type="button" class="btn-g pending__cancel" (click)="cancelInvitation(vm.sentInvitation!.invitationId)">Annuler</button>
        </div>

        <div class="pane__foot" *ngIf="!vm.sentInvitation">
          <button type="button" class="cta-block" [disabled]="!selectedFriend" (click)="sendInvitation()">
            {{ selectedFriend ? 'Inviter ' + selectedFriend.username + ' · ' + selectedTime.label : 'Choisissez un joueur' }}
          </button>
        </div>

        <p class="error" role="alert" *ngIf="vm.error">{{ vm.error }}</p>
      </section>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; flex: 1; }

    .lobby {
      flex: 1; width: 100%; max-width: 520px; margin: 0 auto; padding: 48px 20px 40px;
      display: flex; flex-direction: column;
    }
    .lobby__eyebrow { font: 500 11px var(--font); letter-spacing: .28em; text-transform: uppercase; color: var(--p); }
    .lobby__title { margin-top: 4px; font: 400 40px/1 var(--serif); color: var(--text); }
    .lobby__title em { font-style: italic; font-weight: 500; color: var(--gold); }
    .lobby__sub { margin-top: 6px; font-size: 13px; line-height: 1.4; color: var(--textc); }

    .seg { margin-top: 14px; }

    .pane { flex: 1; display: flex; flex-direction: column; }
    .pane__foot { margin-top: 28px; }
    .label {
      display: block; margin: 18px 0 8px;
      font: 500 11px var(--font); letter-spacing: .22em; text-transform: uppercase; color: var(--textc);
    }

    .times { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
    .time {
      height: 84px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
      background: rgba(255, 255, 255, .03); border: 1px solid var(--border); border-radius: 16px;
      color: var(--textd); cursor: pointer; transition: background .2s, border-color .2s, color .2s, box-shadow .2s, transform .09s;
    }
    .time:hover { border-color: var(--borderh); color: var(--text); }
    .time:active { transform: scale(.97); }
    .time--on, .time--on:hover {
      background: rgba(230, 194, 122, .14); border-color: var(--p); color: var(--gold2);
      box-shadow: 0 0 24px rgba(201, 164, 92, .2);
    }
    .time__mins { font: 500 28px/1 var(--serif); }
    .time__name { font: 500 11px var(--font); letter-spacing: .18em; text-transform: uppercase; }
    .times--compact .time { height: 48px; flex-direction: row; gap: 8px; }
    .times--compact .time__mins { font-size: 22px; }

    .elo-row {
      margin-top: 10px; padding: 10px 16px; display: flex; align-items: center; justify-content: space-between;
      background: rgba(255, 255, 255, .03); border: 1px solid rgba(201, 164, 92, .12); border-radius: 14px;
      font-size: 14px; color: var(--textd);
    }
    .elo-row strong { font: 600 24px var(--serif); color: var(--gold); }

    /* searching: a radar sweeping round a knight */
    .search { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px 0; text-align: center; }
    .radar { position: relative; width: 250px; height: 250px; display: flex; align-items: center; justify-content: center; }
    .radar__ring {
      position: absolute; inset: 0; border-radius: 50%; border: 1px solid rgba(230, 194, 122, .55);
      animation: radar-ping 2.4s cubic-bezier(.2, .6, .3, 1) infinite;
    }
    .radar__ring:nth-child(2) { animation-delay: .8s; }
    .radar__ring:nth-child(3) { animation-delay: 1.6s; }
    .radar__sweep {
      position: absolute; inset: 28px; border-radius: 50%; border: 1px solid rgba(201, 164, 92, .18);
      background: conic-gradient(from 0deg, rgba(230, 194, 122, 0) 0deg, rgba(230, 194, 122, .24) 50deg, rgba(230, 194, 122, 0) 52deg);
      animation: radar-spin 3.2s linear infinite;
    }
    .radar__inner { position: absolute; inset: 70px; border-radius: 50%; border: 1px solid rgba(201, 164, 92, .14); }
    .radar__core {
      position: relative; width: 92px; height: 92px; border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      font: 48px 'Segoe UI Symbol', 'Noto Sans Symbols 2', serif; color: #140f08;
      background: var(--gold-grad); box-shadow: 0 0 50px rgba(230, 194, 122, .4);
    }
    @keyframes radar-ping { 0% { transform: scale(.45); opacity: .85; } 100% { transform: scale(1.15); opacity: 0; } }
    @keyframes radar-spin { to { transform: rotate(360deg); } }
    .search__title { margin-top: 24px; font: 500 28px var(--serif); color: var(--text); }
    .search__sub { margin-top: 6px; font-size: 14px; font-variant-numeric: tabular-nums; color: var(--textc); }
    .search__cancel { margin-top: 26px; border-radius: 14px; height: 50px; }

    .picked {
      margin-top: 8px; padding: 0 14px; height: 50px; display: flex; align-items: center; gap: 12px;
      background: rgba(230, 194, 122, .1); border: 1px solid rgba(230, 194, 122, .6); border-radius: 14px;
    }
    .picked__avatar {
      width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%; display: flex; align-items: center; justify-content: center;
      font: 600 12px var(--font); color: var(--gold); background: #1f1812; border: 1px solid rgba(201, 164, 92, .3);
    }
    .picked__info { flex: 1; display: flex; flex-direction: column; }
    .picked__name { font: 500 15px var(--font); color: var(--text); }
    .picked__elo { font-size: 12px; color: var(--textc); }
    .picked__check {
      width: 22px; height: 22px; padding: 4px; border-radius: 50%; background: var(--gold);
      fill: none; stroke: #140f08; stroke-width: 3; stroke-linecap: round; stroke-linejoin: round;
    }

    .pending {
      margin-top: 24px; padding: 14px 16px; display: flex; align-items: center; gap: 12px;
      background: rgba(255, 255, 255, .03); border: 1px solid var(--border); border-radius: 14px;
      font-size: 14px; color: var(--textd);
    }
    .pending p { flex: 1; }
    .pending strong { color: var(--gold); font-weight: 500; }
    .pending__pulse { width: 10px; height: 10px; border-radius: 50%; background: var(--gold); box-shadow: 0 0 10px var(--gold); animation: glow-pulse 1.2s infinite; }
    .pending__cancel { padding: 8px 16px; font-size: 12px; border-radius: 10px; }

    .error { margin-top: 14px; text-align: center; font-size: 13px; color: #ec8a75; }

    /* phones: the call to action sits at the bottom, by the thumb */
    @media (max-width: 768px) {
      .lobby { max-width: none; padding: 16px 20px 14px; }
      .pane__foot { margin-top: auto; padding-top: 16px; }
      .radar { width: 200px; height: 200px; }
      .radar__core { width: 76px; height: 76px; font-size: 38px; }
      .search__title { margin-top: 16px; font-size: 24px; }
      .search__cancel { margin-top: 16px; height: 44px; }
    }
    @media (max-width: 768px) and (max-height: 740px) {
      .lobby__sub { display: none; }
      .time { height: 68px; }
      .elo-row { display: none; }
    }
  `],
})
export class OnlineLobbyPage implements OnInit, OnDestroy {
  private store = inject(Store);
  private searching = false;
  private sub = new Subscription();

  activeTab: Tab = 'random';
  selectedFriend: UserSummary | null = null;

  timeOptions: TimeOption[] = [
    { label: '5′',  name: 'Blitz',     type: 'blitz',     initialMs: 5  * 60_000, incrementMs: 0 },
    { label: '10′', name: 'Rapide',    type: 'rapid',     initialMs: 10 * 60_000, incrementMs: 0 },
    { label: '30′', name: 'Classique', type: 'classical', initialMs: 30 * 60_000, incrementMs: 0 },
  ];
  selectedTime = this.timeOptions[1];

  vm$ = combineLatest({
    isSearching: this.store.select(selectIsSearching),
    queueEntry:  this.store.select(selectQueueEntry),
    sentInvitation: this.store.select(selectSentInvitation),
    error: this.store.select(selectLobbyError),
    user: this.store.select(selectUser),
    tick: timer(0, 1000),
  }).pipe(map((vm) => ({ ...vm, waited: elapsedSince(vm.queueEntry?.joinedAt) })));

  ngOnInit(): void {
    // An error from an earlier visit is not news
    this.store.dispatch(LobbyActions.clearError());
    this.store.dispatch(LobbyActions.loadPendingInvitations());
    this.sub.add(this.store.select(selectIsSearching).subscribe((s) => (this.searching = s)));
  }

  ngOnDestroy(): void {
    // Someone who walked away is no longer waiting for a game: leaving them in the queue paired
    // them with an opponent they would never see, and dragged them out of whatever they were doing
    if (this.searching) this.store.dispatch(LobbyActions.leaveQueue());
    this.sub.unsubscribe();
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
}
