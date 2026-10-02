import { fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { ReconnectingSocket, SocketLike, SocketState } from './reconnecting-socket';

class FakeSocket implements SocketLike {
  readyState = 0;
  closed = false;
  sent: string[] = [];
  onopen: ((e: unknown) => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: ((e: unknown) => void) | null = null;
  onerror: ((e: unknown) => void) | null = null;
  constructor(readonly url: string) {}
  close(): void { this.closed = true; this.readyState = 3; }
  send(data: string): void { this.sent.push(data); }
  /** The server accepted the connection. */
  accept(): void { this.readyState = 1; this.onopen?.({}); }
  /** The connection dropped without us asking. */
  drop(): void { this.readyState = 3; this.onclose?.({}); }
}

describe('ReconnectingSocket', () => {
  let sockets: FakeSocket[];
  let states: SocketState[];
  let opens: boolean[];
  let messages: string[];
  let urls: Array<string | null>;
  let prepare: (() => Observable<unknown>) | undefined;

  const last = () => sockets[sockets.length - 1];

  function makeSocket(): ReconnectingSocket {
    return new ReconnectingSocket({
      url: () => (urls.length ? urls.shift()! : 'ws://x/game'),
      prepare: prepare ? () => prepare!() : undefined,
      onMessage: (m) => messages.push(m),
      onOpen: (reconnected) => opens.push(reconnected),
      onStateChange: (s) => states.push(s),
      create: (url) => { const s = new FakeSocket(url); sockets.push(s); return s; },
      baseDelayMs: 1000,
      maxDelayMs: 8000,
    });
  }

  beforeEach(() => {
    sockets = []; states = []; opens = []; messages = []; urls = []; prepare = undefined;
  });

  it('connects once, however often open() is called', () => {
    const socket = makeSocket();
    socket.open();
    socket.open();

    expect(sockets.length).toBe(1);
    last().accept();
    socket.open();
    expect(sockets.length).toBe(1);
    expect(opens).toEqual([false]);
  });

  it('reconnects after a drop, doubling the delay up to the maximum', fakeAsync(() => {
    const socket = makeSocket();
    socket.open();
    last().drop();                       // never opened: retry in 1s
    tick(999);
    expect(sockets.length).toBe(1);
    tick(1);
    expect(sockets.length).toBe(2);

    last().drop();                       // 2s
    tick(1999);
    expect(sockets.length).toBe(2);
    tick(1);
    expect(sockets.length).toBe(3);

    last().drop(); tick(4000);           // 4s
    last().drop(); tick(8000);           // 8s (the maximum)
    last().drop(); tick(8000);           // still 8s
    expect(sockets.length).toBe(6);
    socket.close();
  }));

  it('starts the delay over after a successful connection, and says it is a reconnect', fakeAsync(() => {
    const socket = makeSocket();
    socket.open();
    last().drop(); tick(1000);           // 1s
    last().drop(); tick(2000);           // 2s
    last().accept();                     // connected: delay resets
    last().drop();

    tick(1000);
    expect(sockets.length).toBe(4);
    last().accept();
    expect(opens).toEqual([false, true]); // the first success is not a reconnect; the next one is
    socket.close();
  }));

  it('asks for a fresh URL on every attempt', fakeAsync(() => {
    urls = ['ws://x/game?token=old', 'ws://x/game?token=new'];
    const socket = makeSocket();
    socket.open();
    last().drop(); tick(1000);

    expect(sockets.map((s) => s.url)).toEqual(['ws://x/game?token=old', 'ws://x/game?token=new']);
    socket.close();
  }));

  it('runs prepare before each attempt and retries if it fails', fakeAsync(() => {
    let calls = 0;
    prepare = () => (++calls === 1 ? throwError(() => new Error('refresh failed')) : of(true));
    const socket = makeSocket();

    socket.open();
    expect(sockets.length).toBe(0);      // prepare failed: no connection yet
    tick(1000);
    expect(sockets.length).toBe(1);
    expect(calls).toBe(2);
    socket.close();
  }));

  it('stops for good when there is no URL any more (logged out)', fakeAsync(() => {
    urls = ['ws://x/game', null];
    const socket = makeSocket();
    socket.open();
    last().drop(); tick(1000);

    expect(sockets.length).toBe(1);
    expect(states[states.length - 1]).toBe('closed');
    tick(60_000);
    expect(sockets.length).toBe(1);
  }));

  it('does not reconnect after close()', fakeAsync(() => {
    const socket = makeSocket();
    socket.open();
    const first = last();
    first.accept();

    socket.close();
    first.drop();                        // the late close event of the socket we closed
    tick(60_000);

    expect(first.closed).toBeTrue();
    expect(sockets.length).toBe(1);
  }));

  it('ignores the late events of a socket it has replaced', fakeAsync(() => {
    const socket = makeSocket();
    socket.open();
    const old = last();
    old.drop(); tick(1000);
    const current = last();
    current.accept();

    old.onmessage?.({ data: 'stale' });
    old.drop();

    expect(messages).toEqual([]);
    tick(60_000);
    expect(sockets.length).toBe(2);
    socket.close();
  }));

  it('passes messages through and sends only while open', () => {
    const socket = makeSocket();
    socket.open();
    socket.send('too early');
    last().accept();
    last().onmessage?.({ data: '{"type":"X"}' });
    socket.send('hello');

    expect(messages).toEqual(['{"type":"X"}']);
    expect(last().sent).toEqual(['hello']);
  });

  it('reports its state', fakeAsync(() => {
    const socket = makeSocket();
    socket.open();
    last().accept();
    last().drop();
    socket.close();

    expect(states).toEqual(['connecting', 'open', 'reconnecting', 'closed']);
  }));
});
