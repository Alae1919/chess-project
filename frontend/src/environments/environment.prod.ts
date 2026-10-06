// ─────────────────────────────────────────────────────────────────────────────
// src/environments/environment.prod.ts
// ─────────────────────────────────────────────────────────────────────────────
export const environment = {
    production: true,
    apiUrl: '/api',
    // wss on an https page: browsers refuse a plain ws connection from a secure page
    wsUrl:  (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host + '/ws',
  };