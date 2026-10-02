import { Game, PieceColor } from '../models';

export interface ResultSummary {
  /** "Victoire", "Défaite", "Match nul", or "Victoire des Blancs" when there is no "you" */
  headline: string;
  /** How it ended: "Échec et mat", "Temps écoulé"... */
  reason: string;
  tone: 'win' | 'loss' | 'draw' | 'neutral';
  /** Rating points gained or lost by the viewer; only for rated online games */
  eloChange: number | null;
}

const REASONS: Record<string, string> = {
  checkmate: 'Échec et mat',
  stalemate: 'Pat',
  resignation: 'Abandon',
  timeout: 'Temps écoulé',
  draw_agreement: 'Nulle par accord mutuel',
  threefold_repetition: 'Triple répétition',
  fifty_move_rule: 'Règle des 50 coups',
  insufficient_material: 'Matériel insuffisant',
  abandonment: 'Déconnexion',
};

/**
 * Which side the viewer plays: their seat in an online game, the human's seat
 * against the AI, and none in a local game (one person plays both sides).
 */
export function playerColorOf(game: Game, userId?: string): PieceColor | null {
  if (game.mode === 'local') return null;
  if (game.playerWhite.isAi) return 'black';
  if (game.playerBlack.isAi) return 'white';
  if (!userId) return null;
  if (game.playerWhite.userId === userId) return 'white';
  if (game.playerBlack.userId === userId) return 'black';
  return null;
}

export function describeReason(reason: string | undefined): string {
  if (!reason) return '';
  const key = reason.replace(/-/g, '_');
  return REASONS[key] ?? reason;
}

/** The finished game from the viewer's point of view, or null while it is still on. */
export function summarizeResult(game: Game, myColor: PieceColor | null): ResultSummary | null {
  const result = game.result;
  if (!result) return null;

  const reason = describeReason(result.reason);
  const change = myColor === 'white' ? result.whiteEloChange
               : myColor === 'black' ? result.blackEloChange
               : null;
  const eloChange = change ?? null;

  if (!result.winner) return { headline: 'Match nul', reason, tone: 'draw', eloChange };
  if (!myColor) {
    const side = result.winner === 'white' ? 'des Blancs' : 'des Noirs';
    return { headline: `Victoire ${side}`, reason, tone: 'neutral', eloChange: null };
  }
  return result.winner === myColor
    ? { headline: 'Victoire', reason, tone: 'win', eloChange }
    : { headline: 'Défaite', reason, tone: 'loss', eloChange };
}
