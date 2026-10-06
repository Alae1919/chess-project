


// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/account/pages/account.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { combineLatest } from 'rxjs';
import { AccountActions } from '../../../store/account/account.actions';
import {
  selectUser,
  selectUserStats,
  selectMatchHistory,
  selectAchievements,
  selectUserPreferences,
  selectAccountError,
} from '../../../store/account/account.reducer';
import { Achievement, UserPreferences } from '../../../core/models/index';
import { AuthService } from '../../../core/services/auth.service';
import { BoardStylePickerComponent } from '../../../shared/components/board-style-picker/board-style-picker.component';

@Component({
  selector: 'app-account-page',
  standalone: true, 
  imports: [CommonModule, AsyncPipe, FormsModule, BoardStylePickerComponent],
  templateUrl: './account.page.html',
  styleUrls: ['./account.page.scss'],
})
export class AccountPage implements OnInit {
  private store = inject(Store);
  private auth = inject(AuthService);

  vm$ = combineLatest({
    user:        this.store.select(selectUser),
    stats:       this.store.select(selectUserStats),
    history:     this.store.select(selectMatchHistory),
    achievements:this.store.select(selectAchievements),
    prefs:       this.store.select(selectUserPreferences),
  });

  ngOnInit(): void {
    this.store.dispatch(AccountActions.loadProfile());
    this.store.dispatch(AccountActions.loadMatchHistory({ page: 0 }));
    this.store.dispatch(AccountActions.loadAchievements());
  }

  updatePref(key: keyof UserPreferences, value: any): void {
    this.store.dispatch(AccountActions.updatePreferences({ prefs: { [key]: value } }));
  }

  /** The store is wiped when the session ends, so nothing else needs resetting here */
  logout(): void {
    this.auth.logout();
  }

  /** The password field of the delete form is open */
  confirmingDelete = false;
  deletePassword = '';
  readonly deleteError$ = this.store.select(selectAccountError);

  startDelete(): void { this.confirmingDelete = true; this.deletePassword = ''; }
  cancelDelete(): void { this.confirmingDelete = false; this.deletePassword = ''; }

  /** Deleting needs the password again, so a stolen login can't remove the account */
  deleteAccount(): void {
    if (!this.deletePassword) return;
    this.store.dispatch(AccountActions.deleteAccount({ password: this.deletePassword }));
  }

  winRateLabel(stats: any): string {
    if (!stats) return '0%';
    // the API already sends a percentage (0–100)
    return `${Math.round(stats.winRate)}%`;
  }

  eloDeltaLabel(delta?: number | null): string {
    if (delta == null) return '—';
    return delta > 0 ? `+${delta}` : `${delta}`;
  }

  // Add this inside the AccountPage class
  unlockedCount(achievements: Achievement[] | null | undefined): number {
    if (!achievements) return 0;
    return achievements.filter(a => a.unlocked).length;
  }
}
