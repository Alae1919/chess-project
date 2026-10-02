import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, Subject, of } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { ReconnectingSocket } from '../utils/reconnecting-socket';

export type LobbyWsEventType =
  | 'MATCH_FOUND'
  | 'INVITE_RECEIVED'
  | 'INVITE_DECLINED'
  | 'INVITE_CANCELLED'
  | 'INVITE_EXPIRED';

export interface LobbyWsEvent<T = unknown> {
  type: LobbyWsEventType;
  payload: T;
}

/** The lobby socket (match found, invitations). Reconnects by itself while logged in. */
@Injectable({ providedIn: 'root' })
export class LobbyWebSocketService {
  private authService = inject(AuthService);
  private messageSubject = new Subject<LobbyWsEvent>();
  private socket = new ReconnectingSocket({
    url: () => {
      const token = this.authService.accessToken;
      return token ? `${environment.wsUrl}/lobby?token=${token}` : null;
    },
    prepare: () => this.freshToken(),
    onMessage: (data) => {
      try {
        this.messageSubject.next(JSON.parse(data) as LobbyWsEvent);
      } catch {
        console.error('Lobby WS parse error', data);
      }
    },
  });

  messages$ = this.messageSubject.asObservable();

  /** Starts the socket; calling it again while it runs does nothing. */
  connect(): void {
    this.socket.open();
  }

  disconnect(): void {
    this.socket.close();
  }

  private freshToken(): Observable<unknown> {
    if (!this.authService.isLoggedIn) return EMPTY;
    return this.authService.isAccessTokenExpired() ? this.authService.refreshToken() : of(true);
  }
}
