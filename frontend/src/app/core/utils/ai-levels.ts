// The AI's six levels: their names and approximate strength.
import { AiDifficulty } from '../models';

export const AI_LEVEL_LABELS = ['Facile', 'Moyen', 'Difficile', 'Expert', 'Maître', 'Maximum'] as const;

/** Approximate strength of each level, measured against Stockfish (see docs/how-the-ai-works.md) */
export const AI_LEVEL_ELO = [1100, 1400, 1750, 2050, 2550, 2900] as const;

export function aiLevelLabel(level?: AiDifficulty | number): string {
  return (level && AI_LEVEL_LABELS[level - 1]) || 'IA';
}

export function aiLevelElo(level?: AiDifficulty | number): number | null {
  return (level && AI_LEVEL_ELO[level - 1]) || null;
}
