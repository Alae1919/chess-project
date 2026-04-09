// ─────────────────────────────────────────────────────────────────────────────
// src/app/core/services/websocket.service.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

export interface WsEvent<T = unknown> {
  type: 'MOVE_MADE' | 'GAME_UPDATED' | 'CHAT_MESSAGE' | 'PLAYER_JOINED' | 'GAME_OVER' | 'TIMER_TICK';
  payload: T;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private authService = inject(AuthService);
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<WsEvent>();

  messages$ = this.messageSubject.asObservable();

  connect(gameId: string): void {
    const token = this.authService.accessToken;
    const url = `${environment.wsUrl}/game/${gameId}?token=${token}`;
    this.socket = new WebSocket(url);

    this.socket.onmessage = (event) => {
      try {
        const data: WsEvent = JSON.parse(event.data);
        this.messageSubject.next(data);
      } catch {
        console.error('WS parse error', event.data);
      }
    };

    this.socket.onerror = (err) => console.error('WebSocket error', err);
    this.socket.onclose = () => console.log('WebSocket closed');
  }

  send<T>(event: WsEvent<T>): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(event));
    }
  }

  disconnect(): void {
    this.socket?.close();
    this.socket = null;
  }
}
