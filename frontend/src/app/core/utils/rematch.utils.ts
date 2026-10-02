import { Game, GameOptions, PieceColor } from '../models';
import { SendInvitationRequest } from '../services/invitation.service';

const opposite = (color: PieceColor): PieceColor => (color === 'white' ? 'black' : 'white');

/** Options for the same game again against the AI, or locally, with colours swapped. */
export function rematchOptions(game: Game, myColor: PieceColor | null): GameOptions {
  const ai = game.playerWhite.isAi ? game.playerWhite : game.playerBlack.isAi ? game.playerBlack : null;
  return {
    mode: game.mode,
    aiDifficulty: ai?.aiDifficulty ?? 4,
    playerColor: myColor ? opposite(myColor) : 'white',
    timeControl: game.timeControl,
    enableUndo: true,
    confirmMoves: false,
    showLegalMoves: true,
    realTimeAnalysis: false,
  };
}

/** An invitation to the same opponent, same clock, colours swapped. */
export function rematchInvitation(game: Game, myColor: PieceColor): SendInvitationRequest {
  const opponent = myColor === 'white' ? game.playerBlack : game.playerWhite;
  return {
    inviteeUsername: opponent.username,
    timeControlType: game.timeControl.type,
    timeControlInitialMs: game.timeControl.initialMs,
    timeControlIncrementMs: game.timeControl.incrementMs,
    inviterColor: opposite(myColor),
  };
}
