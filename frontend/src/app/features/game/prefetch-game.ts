// src/app/features/game/prefetch-game.ts
import { BoardMode } from '../../core/models';
import { isCompactViewport } from '../../core/utils/viewport';
import { prefetchSceneAssets } from '../../shared/three/scene-assets';

let done = false;

/**
 * Fetch what a game will need while the player is still choosing one, once the page is idle: the game page's code,
 * and for the 3D board its code (three.js), the piece models and, on wide screens, the desk. "Start" then opens a
 * board that has nothing left to download. Once per page load.
 */
export function prefetchGame(mode: BoardMode): void {
  if (done) return;
  done = true;
  whenIdle(() => {
    void import('./pages/game.page').catch(() => undefined);
    if (mode !== '3d') return;
    void import('../../shared/components/chess-board-3d/chess-board-3d.component').catch(() => undefined);
    // the same test as the game page's: wide screens get the desk
    prefetchSceneAssets(!isCompactViewport());
  });
}

function whenIdle(work: () => void): void {
  const ric = (window as { requestIdleCallback?: (cb: () => void, o?: { timeout: number }) => number }).requestIdleCallback;
  if (ric) ric(work, { timeout: 4000 });
  else setTimeout(work, 1000);
}
