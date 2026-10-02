import { Observable, take } from 'rxjs';

/** The part of WebSocket this helper uses; tests supply a fake. */
export interface SocketLike {
  readyState: number;
  close(): void;
  send(data: string): void;
  onopen: ((event: unknown) => void) | null;
  onmessage: ((event: { data: string }) => void) | null;
  onclose: ((event: unknown) => void) | null;
  onerror: ((event: unknown) => void) | null;
}

export type SocketState = 'closed' | 'connecting' | 'open' | 'reconnecting';

export interface ReconnectingSocketOptions {
  /** URL for the next attempt, or null to stop for good (e.g. the user logged out). */
  url: () => string | null;
  /** Runs before every attempt, e.g. to refresh a token that is about to expire. */
  prepare?: () => Observable<unknown>;
  onMessage: (data: string) => void;
  /** {@code reconnected} is false for the first connection and true after a drop. */
  onOpen?: (reconnected: boolean) => void;
  onStateChange?: (state: SocketState) => void;
  create?: (url: string) => SocketLike;
  baseDelayMs?: number;
  maxDelayMs?: number;
}

/**
 * A WebSocket that comes back by itself: after an unexpected close it retries with
 * a growing delay (reset by every successful connection), asks for a fresh URL each
 * time, and never has two sockets open or connecting at once. {@link close} stops it.
 */
export class ReconnectingSocket {
  private socket: SocketLike | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private preparing = false;
  private wanted = false;
  private everOpened = false;
  private delay: number;

  private readonly base: number;
  private readonly max: number;

  constructor(private readonly options: ReconnectingSocketOptions) {
    this.base = options.baseDelayMs ?? 1000;
    this.max = options.maxDelayMs ?? 30_000;
    this.delay = this.base;
  }

  /** Connects, and keeps reconnecting until {@link close}. Does nothing if already running. */
  open(): void {
    if (this.wanted) return;
    this.wanted = true;
    this.setState('connecting');
    this.attempt();
  }

  close(): void {
    this.wanted = false;
    this.everOpened = false;
    this.delay = this.base;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    const socket = this.socket;
    this.socket = null;
    socket?.close();
    this.setState('closed');
  }

  send(data: string): void {
    if (this.socket?.readyState === 1) this.socket.send(data);
  }

  private attempt(): void {
    if (!this.wanted || this.socket || this.preparing) return;

    if (!this.options.prepare) {
      this.connect();
      return;
    }
    this.preparing = true;
    this.options.prepare().pipe(take(1)).subscribe({
      error: () => { this.preparing = false; this.retryLater(); },
      complete: () => { this.preparing = false; this.connect(); },
    });
  }

  private connect(): void {
    if (!this.wanted || this.socket) return;
    const url = this.options.url();
    if (!url) { this.close(); return; }

    const socket = (this.options.create ?? ((u: string) => new WebSocket(u) as unknown as SocketLike))(url);
    this.socket = socket;

    socket.onopen = () => {
      if (this.socket !== socket) return;
      const reconnected = this.everOpened;
      this.everOpened = true;
      this.delay = this.base;
      this.setState('open');
      this.options.onOpen?.(reconnected);
    };
    socket.onmessage = (event) => {
      if (this.socket === socket) this.options.onMessage(event.data);
    };
    socket.onclose = () => {
      if (this.socket !== socket) return; // a socket we already let go of
      this.socket = null;
      this.retryLater();
    };
  }

  private retryLater(): void {
    if (!this.wanted) return;
    this.setState('reconnecting');
    const wait = this.delay;
    this.delay = Math.min(this.delay * 2, this.max);
    this.timer = setTimeout(() => { this.timer = null; this.attempt(); }, wait);
  }

  private setState(state: SocketState): void {
    this.options.onStateChange?.(state);
  }
}
