import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Game, GameInvitation, UserSummary } from '../models';

export interface SendInvitationRequest {
  inviteeUsername: string;
  timeControlType: string;
  timeControlInitialMs: number;
  timeControlIncrementMs: number;
  /** The colour the inviter wants; omit to draw at random (a rematch swaps colours) */
  inviterColor?: 'white' | 'black';
}

@Injectable({ providedIn: 'root' })
export class InvitationService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/invitations`;
  private usersBase = `${environment.apiUrl}/users`;

  sendInvitation(req: SendInvitationRequest): Observable<GameInvitation> {
    return this.http.post<GameInvitation>(this.base, req);
  }

  getPendingInvitations(): Observable<GameInvitation[]> {
    return this.http.get<GameInvitation[]>(`${this.base}/pending`);
  }

  respondToInvitation(invitationId: string, response: 'accept' | 'decline'): Observable<Game | GameInvitation> {
    return this.http.post<Game | GameInvitation>(`${this.base}/${invitationId}/respond`, { response });
  }

  cancelInvitation(invitationId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${invitationId}`);
  }

  searchUsers(username: string): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${this.usersBase}/search`, { params: { username } });
  }
}
