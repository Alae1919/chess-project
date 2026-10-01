// src/app/shared/board/board-styles.ts
// The board modes and styles a player can pick from (labels and swatches for the pickers).
import { BoardMode, Style2D, Style3D } from '../../core/models';

export interface StyleOption<T extends string> {
  id: T;
  label: string;
  /** Swatch colours for the picker: light square, dark square, accent (frame / pieces) */
  swatch: [string, string, string];
}

export const BOARD_MODES: ReadonlyArray<{ id: BoardMode; label: string; hint: string }> = [
  { id: '3d', label: '3D', hint: 'Plateau en perspective, pièces sculptées' },
  { id: '2d', label: '2D', hint: 'Plateau plat, vue classique' },
];

export const STYLES_3D: ReadonlyArray<StyleOption<Style3D>> = [
  { id: 'marble-gold', label: 'Marbre & Or', swatch: ['#d8c9b2', '#121010', '#e2b25e'] },
  { id: 'classic-wood', label: 'Bois classique', swatch: ['#d0a46e', '#7a4a28', '#f7ecd2'] },
  { id: 'ebony-ivory', label: 'Ébène & Ivoire', swatch: ['#c9bca3', '#16120f', '#f8f3e4'] },
];

export const STYLES_2D: ReadonlyArray<StyleOption<Style2D>> = [
  { id: 'classic-wood', label: 'Bois classique', swatch: ['#f0d9b5', '#b58863', '#c9a84c'] },
  { id: 'luxe', label: 'Luxe', swatch: ['#d8c9b2', '#2a2118', '#d6a94e'] },
  { id: 'slate-blue', label: 'Ardoise bleue', swatch: ['#c9d4df', '#5b738b', '#8fb0d0'] },
];

export const DEFAULT_MODE: BoardMode = '3d';
export const DEFAULT_STYLE_3D: Style3D = 'marble-gold';
export const DEFAULT_STYLE_2D: Style2D = 'classic-wood';

export const isBoardMode = (v: unknown): v is BoardMode => v === '2d' || v === '3d';
export const isStyle3D = (v: unknown): v is Style3D => STYLES_3D.some((s) => s.id === v);
export const isStyle2D = (v: unknown): v is Style2D => STYLES_2D.some((s) => s.id === v);
