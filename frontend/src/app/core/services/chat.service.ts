// ─────────────────────────────────────────────────────────────────────────────
// src/app/core/services/chat.service.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatMessage } from '../models';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/chat`;

  getMessages(gameId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.base}/${gameId}`);
  }

  sendMessage(gameId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.base}/${gameId}`, { content });
  }
}