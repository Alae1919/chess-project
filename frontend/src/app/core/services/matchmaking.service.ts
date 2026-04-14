import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { QueueEntry } from '../models';

export interface JoinQueueRequest {
  timeControlType: string;
  timeControlInitialMs: number;
  timeControlIncrementMs: number;
}

@Injectable({ providedIn: 'root' })
export class MatchmakingService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/matchmaking`;

  joinQueue(req: JoinQueueRequest): Observable<QueueEntry> {
    return this.http.post<QueueEntry>(`${this.base}/queue`, req);
  }

  leaveQueue(): Observable<void> {
    return this.http.delete<void>(`${this.base}/queue`);
  }

  getQueueStatus(): Observable<QueueEntry> {
    return this.http.get<QueueEntry>(`${this.base}/queue/status`);
  }
}
