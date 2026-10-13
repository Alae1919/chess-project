// src/app/features/landing/landing-media.ts
// The footage of acts II and III. Each shot has stills (WebP, 800 and 1600 px wide), which are its
// poster and what phones, slow connections and reduced-motion visitors see. A shot may also have a
// video clip: drop it in src/assets/landing and name it here, e.g.
//   video: { src: 'assets/landing/act3-spread.mp4', type: 'video/mp4' }
// It then plays (muted, inline) while its shot is on screen, and pauses when it leaves.

export interface Shot {
  id: 'hand' | 'knight' | 'spread' | 'branch' | 'converge';
  /** Path without the "-800.webp" / "-1600.webp" ending */
  still: string;
  /** Where the subject is, for `object-position` when the screen crops the frame */
  focus: string;
  video?: { src: string; type: string };
}

export const SHOTS: Shot[] = [
  { id: 'hand', still: 'assets/landing/act2-hand', focus: '42% 60%' },
  { id: 'knight', still: 'assets/landing/act2-knight', focus: '46% 60%' },
  { id: 'spread', still: 'assets/landing/act3-spread', focus: '42% 62%' },
  { id: 'branch', still: 'assets/landing/act3-branch', focus: '52% 60%' },
  { id: 'converge', still: 'assets/landing/act3-converge', focus: '50% 50%' },
];
