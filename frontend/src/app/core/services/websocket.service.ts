// ─────────────────────────────────────────────────────────────────────────────
// src/app/core/services/websocket.service.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, EMPTY, Observable, Subject, of } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { ReconnectingSocket, SocketState } from '../utils/reconnecting-socket';

export interface WsEvent<T = unknown> {
  type:
    | 'MOVE_MADE' | 'GAME_UPDATED' | 'CHAT_MESSAGE' | 'PLAYER_JOINED' | 'GAME_OVER' | 'TIMER_TICK'
    | 'OPPONENT_DISCONNECTED' | 'OPPONENT_RECONNECTED' | 'DRAW_OFFERED' | 'DRAW_DECLINED';
  payload: T;
}

/**
 * The socket of the game being played. It reconnects by itself after a drop and
 * announces {@link reconnected$}, so the store can fetch whatever was missed.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private authService = inject(AuthService);
  private socket: ReconnectingSocket | null = null;
  private gameId: string | null = null;
  private messageSubject = new Subject<WsEvent>();
  private reconnectedSubject = new Subject<void>();
  private stateSubject = new BehaviorSubject<SocketState>('closed');

  messages$ = this.messageSubject.asObservable();
  /** Emits each time the socket is back after a drop (not for the first connection). */
  reconnected$ = this.reconnectedSubject.asObservable();
  connectionState$ = this.stateSubject.asObservable();

  /** Connects to a game; a no-op if this game's socket is already running. */
  connect(gameId: string): void {
    if (this.socket && this.gameId === gameId) {
      this.socket.open();
      return;
    }
    this.socket?.close();
    this.gameId = gameId;
    this.socket = new ReconnectingSocket({
      // read each time: a reconnect after a long outage needs the refreshed token
      url: () => {
        const token = this.authService.accessToken;
        return token ? `${environment.wsUrl}/game/${gameId}?token=${token}` : null;
      },
      prepare: () => this.freshToken(),
      onMessage: (data) => {
        try {
          this.messageSubject.next(JSON.parse(data) as WsEvent);
        } catch {
          console.error('WS parse error', data);
        }
      },
      onOpen: (reconnected) => { if (reconnected) this.reconnectedSubject.next(); },
      onStateChange: (state) => this.stateSubject.next(state),
    });
    this.socket.open();
  }

  send<T>(event: WsEvent<T>): void {
    this.socket?.send(JSON.stringify(event));
  }

  disconnect(): void {
    this.socket?.close();
    this.socket = null;
    this.gameId = null;
  }

  private freshToken(): Observable<unknown> {
    if (!this.authService.isLoggedIn) return EMPTY;
    return this.authService.isAccessTokenExpired() ? this.authService.refreshToken() : of(true);
  }
}
