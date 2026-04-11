// src/app/core/services/account.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { User, UserPreferences, MatchHistory, Achievement } from '../models';

/** Backend: UserController @ /api/users */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/users`;

  getProfile(): Observable<User> {
    return this.http.get<User>(`${this.base}/me`);
  }

  updateProfile(data: Partial<Pick<User, 'username' | 'country' | 'avatarUrl'>>): Observable<User> {
    return this.http.patch<User>(`${this.base}/me`, data);
  }

  updatePreferences(prefs: Partial<UserPreferences>): Observable<UserPreferences> {
    return this.http.patch<UserPreferences>(`${this.base}/me/preferences`, prefs);
  }

  getMatchHistory(page = 0, size = 20): Observable<{ content: MatchHistory[]; total: number }> {
    return this.http
      .get<{
        content: MatchHistory[];
        totalElements: number;
      }>(`${this.base}/me/match-history?page=${page}&size=${size}`)
      .pipe(map((r) => ({ content: r.content, total: r.totalElements })));
  }

  getAchievements(): Observable<Achievement[]> {
    return this.http.get<Achievement[]>(`${this.base}/me/achievements`);
  }

  changePassword(oldPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/users/me/change-password`, {
      oldPassword,
      newPassword,
    });
  }

  deleteAccount(): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/users/me`);
  }
}
