import { GameStatus } from '../models';

export function isPlayableStatus(status: GameStatus | null | undefined): boolean {
  return status === 'active' || status === 'check';
}

export function isTerminalStatus(status: GameStatus | null | undefined): boolean {
  return status === 'checkmate' || status === 'stalemate'
    || status === 'draw_50_move' || status === 'draw_insufficient_material'
    || status === 'draw_repetition' || status === 'white_resigned'
    || status === 'black_resigned' || status === 'draw_agreed'
    || status === 'finished' || status === 'aborted';
}
