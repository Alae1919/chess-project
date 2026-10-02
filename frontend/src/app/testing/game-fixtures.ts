// Test-only builders for store and service specs. Not imported by app code.
import { ChatMessage, Game, GamePlayer } from '../core/models';

function player(color: 'white' | 'black', overrides: Partial<GamePlayer> = {}): GamePlayer {
  return {
    username: color === 'white' ? 'alice' : 'bob',
    color,
    timeRemainingMs: 300_000,
    capturedPieces: [],
    ...overrides,
  };
}

export function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: 'game-1',
    mode: 'ai',
    status: 'active',
    playerWhite: player('white'),
    playerBlack: player('black'),
    board: { squares: Array.from({ length: 8 }, () => Array(8).fill(null)) },
    moves: [],
    currentTurn: 'white',
    timeControl: { type: 'blitz', initialMs: 300_000, incrementMs: 0 },
    enPassantTarget: null,
    castlingRights: { whiteKingside: true, whiteQueenside: true, blackKingside: true, blackQueenside: true },
    halfMoveClock: 0,
    fullMoveNumber: 1,
    createdAt: new Date(0),
    updatedAt: new Date(0),
    ...overrides,
  };
}

export function makeChatMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 'msg-1',
    gameId: 'game-1',
    senderId: 'user-1',
    senderUsername: 'alice',
    content: 'gl hf',
    sentAt: new Date(0),
    ...overrides,
  };
}
