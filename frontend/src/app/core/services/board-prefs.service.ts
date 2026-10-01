// src/app/core/services/board-prefs.service.ts
import { Injectable, inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { BehaviorSubject, combineLatest, distinctUntilChanged, map } from 'rxjs';
import { BoardMode, Style2D, Style3D } from '../models';
import { AccountActions } from '../../store/account/account.actions';
import { selectUserPreferences } from '../../store/account/account.reducer';
import { AuthService } from './auth.service';
import { isWebGLAvailable } from '../../shared/three/luxe-board-scene';
import {
  DEFAULT_MODE,
  DEFAULT_STYLE_2D,
  DEFAULT_STYLE_3D,
  isBoardMode,
  isStyle2D,
  isStyle3D,
} from '../../shared/board/board-styles';

export interface BoardPrefs {
  mode: BoardMode;
  style3d: Style3D;
  style2d: Style2D;
}

const STORAGE_KEY = 'rex_board_prefs';
/** After a local change, ignore the account's older value for this long (the PATCH is still in flight) */
const LOCAL_WINS_MS = 3000;

/**
 * The player's board mode (3D / 2D) and the style picked for each.
 *
 * Saved in the browser so it applies instantly and works for guests, and mirrored to the account
 * when logged in, so it follows the player to other devices. When the account already has values
 * they win over the browser's.
 */
@Injectable({ providedIn: 'root' })
export class BoardPrefsService {
  private store = inject(Store);
  private auth = inject(AuthService);

  private subject = new BehaviorSubject<BoardPrefs>(this.read());
  private lastLocalChange = 0;

  readonly webgl = isWebGLAvailable();

  readonly prefs$ = this.subject.pipe(
    distinctUntilChanged((a, b) => a.mode === b.mode && a.style3d === b.style3d && a.style2d === b.style2d),
  );

  /** What to actually draw: the chosen mode, or 2D when the browser can't do WebGL */
  readonly effectiveMode$ = this.prefs$.pipe(map((p) => (p.mode === '3d' && !this.webgl ? '2d' : p.mode)));

  readonly view$ = combineLatest({ prefs: this.prefs$, mode: this.effectiveMode$ });

  constructor() {
    this.store.select(selectUserPreferences).subscribe((account) => {
      if (!account || Date.now() - this.lastLocalChange < LOCAL_WINS_MS) return;
      // fields the backend doesn't know about yet come back undefined: keep the local choice for those
      const cur = this.subject.value;
      this.apply({
        mode: isBoardMode(account.boardMode) ? account.boardMode : cur.mode,
        style3d: isStyle3D(account.boardStyle3d) ? account.boardStyle3d : cur.style3d,
        style2d: isStyle2D(account.boardStyle2d) ? account.boardStyle2d : cur.style2d,
      });
    });
  }

  get value(): BoardPrefs {
    return this.subject.value;
  }

  setMode(mode: BoardMode): void { this.change({ mode }); }
  setStyle3d(style3d: Style3D): void { this.change({ style3d }); }
  setStyle2d(style2d: Style2D): void { this.change({ style2d }); }

  private change(patch: Partial<BoardPrefs>): void {
    const next = { ...this.subject.value, ...patch };
    this.lastLocalChange = Date.now();
    this.apply(next);

    if (this.auth.isLoggedIn) {
      this.store.dispatch(
        AccountActions.updatePreferences({
          prefs: { boardMode: next.mode, boardStyle3d: next.style3d, boardStyle2d: next.style2d },
        }),
      );
    }
  }

  private apply(prefs: BoardPrefs): void {
    const cur = this.subject.value;
    if (cur.mode === prefs.mode && cur.style3d === prefs.style3d && cur.style2d === prefs.style2d) return;
    this.subject.next(prefs);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
    } catch { /* storage unavailable */ }
  }

  private read(): BoardPrefs {
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
      return {
        mode: isBoardMode(raw.mode) ? raw.mode : DEFAULT_MODE,
        style3d: isStyle3D(raw.style3d) ? raw.style3d : DEFAULT_STYLE_3D,
        style2d: isStyle2D(raw.style2d) ? raw.style2d : DEFAULT_STYLE_2D,
      };
    } catch {
      return { mode: DEFAULT_MODE, style3d: DEFAULT_STYLE_3D, style2d: DEFAULT_STYLE_2D };
    }
  }
}
