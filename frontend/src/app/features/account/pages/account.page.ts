


// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/account/pages/account.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { Store } from '@ngrx/store';
import { combineLatest } from 'rxjs';
import { AccountActions } from '../../../store/account/account.actions';
import {
  selectUser,
  selectUserStats,
  selectMatchHistory,
  selectAchievements,
  selectUserPreferences,
} from '../../../store/account/account.reducer';
import { Achievement, UserPreferences } from '../../../core/models/index';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-account-page',
  standalone: true, 
  imports: [CommonModule, AsyncPipe],
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

  logout(): void {
    this.auth.logout();
  }

  deleteAccount(): void {
    if (confirm('Supprimer définitivement votre compte ? Cette action est irréversible.')) {
      this.store.dispatch(AccountActions.deleteAccount());
    }
  }

  winRateLabel(stats: any): string {
    if (!stats) return '0%';
    return `${Math.round(stats.winRate * 100)}%`;
  }

  eloDeltaLabel(delta?: number): string {
    if (delta === undefined) return '—';
    return delta > 0 ? `+${delta}` : `${delta}`;
  }

  // Add this inside the AccountPage class
  unlockedCount(achievements: Achievement[] | null | undefined): number {
    if (!achievements) return 0;
    return achievements.filter(a => a.unlocked).length;
  }
}
