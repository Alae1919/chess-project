import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

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

@Injectable({ providedIn: 'root' })
export class LobbyWebSocketService {
  private authService = inject(AuthService);
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<LobbyWsEvent>();
  private reconnectDelay = 2000;
  private shouldReconnect = false;

  messages$ = this.messageSubject.asObservable();

  connect(): void {
    if (this.socket?.readyState === WebSocket.OPEN) return;

    const token = this.authService.accessToken;
    if (!token) return;

    this.shouldReconnect = true;
    const url = `${environment.wsUrl}/lobby?token=${token}`;
    this.socket = new WebSocket(url);

    this.socket.onmessage = (event) => {
      try {
        const data: LobbyWsEvent = JSON.parse(event.data);
        this.messageSubject.next(data);
      } catch {
        console.error('Lobby WS parse error', event.data);
      }
    };

    this.socket.onerror = (err) => console.error('Lobby WebSocket error', err);

    this.socket.onclose = () => {
      if (this.shouldReconnect && this.authService.isLoggedIn) {
        setTimeout(() => {
          this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30_000);
          this.connect();
        }, this.reconnectDelay);
      }
    };
  }

  disconnect(): void {
    this.shouldReconnect = false;
    this.reconnectDelay = 2000;
    this.socket?.close();
    this.socket = null;
  }
}
