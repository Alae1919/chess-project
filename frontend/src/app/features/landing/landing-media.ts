// src/app/features/landing/landing-media.ts
// The footage of acts II and V. Each shot has stills (WebP, two widths), which are its poster and
// what phones, slow connections and reduced-motion visitors see. A shot may also have a video clip:
// drop it in src/assets/landing and name it here, e.g.
//   video: { src: 'assets/landing/act2-knight.mp4', type: 'video/mp4' }
// It then plays (muted, inline) while its shot is on screen, and pauses when it leaves.
// Act I (the king) and act III (the search) are clips played by the scroll: see ScrubVideo.

export interface Shot {
  id: 'hand' | 'knight' | 'kings';
  /** Path without the "-<width>.webp" ending */
  still: string;
  /** The two widths the still comes in, small then large */
  widths: readonly [number, number];
  /** Where the subject is, for `object-position` when the screen crops the frame */
  focus: string;
  /** The same for phones, whose tall screens show only a slice of the frame */
  phoneFocus?: string;
  video?: { src: string; type: string };
}

/** The hand and knight stills were made at 800 and 1600 px; the newer ones at 1280 and 2560 */
const OLD = [800, 1600] as const;
const HIGH = [1280, 2560] as const;

export const SHOTS: Shot[] = [
  { id: 'hand', still: 'assets/landing/act2-hand', widths: OLD, focus: '42% 60%' },
  { id: 'knight', still: 'assets/landing/act2-knight', widths: OLD, focus: '46% 60%' },
  // the two kings face each other; a phone keeps White's, the visitor's
  { id: 'kings', still: 'assets/landing/act5-kings', widths: HIGH, focus: '50% 50%', phoneFocus: '70% 50%' },
];
