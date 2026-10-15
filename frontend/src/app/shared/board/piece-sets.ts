// src/app/shared/board/piece-sets.ts
// The piece sets the flat board can draw. The files are in assets/pieces/<id>/ (see the README there).
import { Piece, PieceSet } from '../../core/models';
import { assetUrl } from '../../core/assets/asset-url';

export interface PieceSetOption {
  id: PieceSet;
  label: string;
  hint: string;
}

export const PIECE_SETS: ReadonlyArray<PieceSetOption> = [
  { id: 'glyph', label: 'Symboles', hint: 'Pièces du texte, toujours disponibles' },
  { id: 'classic', label: 'Classique', hint: 'Dessin traditionnel, trait fin' },
  { id: 'contour', label: 'Contour', hint: 'Silhouettes noires et pièces blanches cernées' },
];

export const DEFAULT_PIECE_SET: PieceSet = 'glyph';

export const isPieceSet = (v: unknown): v is PieceSet => PIECE_SETS.some((s) => s.id === v);

const LETTER: Record<Piece['type'], string> = { king: 'K', queen: 'Q', rook: 'R', bishop: 'B', knight: 'N', pawn: 'P' };

/** The image of a piece in an image set: "/assets/pieces/classic/wN.svg?v=…" for a white knight */
export function pieceImageUrl(set: PieceSet, piece: Pick<Piece, 'color' | 'type'>): string {
  return assetUrl(`/assets/pieces/${set}/${piece.color === 'white' ? 'w' : 'b'}${LETTER[piece.type]}.svg`);
}

/** All twelve images of a set, to load ahead of the first game */
export function pieceImageUrls(set: PieceSet): string[] {
  const types: Piece['type'][] = ['king', 'queen', 'rook', 'bishop', 'knight', 'pawn'];
  return types.flatMap((type) => (['white', 'black'] as const).map((color) => pieceImageUrl(set, { color, type })));
}
