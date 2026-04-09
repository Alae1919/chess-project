// src/app/core/services/account.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User, UserPreferences, MatchHistory, Achievement } from '../models';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/account`;

  getProfile(): Observable<User> {
    return this.http.get<User>(`${this.base}/profile`);
  }

  updateProfile(data: Partial<Pick<User, 'username' | 'country' | 'avatarUrl'>>): Observable<User> {
    return this.http.patch<User>(`${this.base}/profile`, data);
  }

  updatePreferences(prefs: Partial<UserPreferences>): Observable<UserPreferences> {
    return this.http.put<UserPreferences>(`${this.base}/preferences`, prefs);
  }

  getMatchHistory(page = 0, size = 20): Observable<{ content: MatchHistory[]; total: number }> {
    return this.http.get<{ content: MatchHistory[]; total: number }>(
      `${this.base}/history?page=${page}&size=${size}`
    );
  }

  getAchievements(): Observable<Achievement[]> {
    return this.http.get<Achievement[]>(`${this.base}/achievements`);
  }

  changePassword(oldPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/change-password`, { oldPassword, newPassword });
  }

  deleteAccount(): Observable<void> {
    return this.http.delete<void>(`${this.base}`);
  }
}
