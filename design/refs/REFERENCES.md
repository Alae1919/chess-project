# REFERENCES — scouted 2026-10-09

Mechanics, not skins. For each reference name the ONE idea you are taking and how it
changes for this brand. Copying a reference wholesale is slop with better taste.

| # | site | stack detected | page shape | mechanics |
|---|---|---|---|---|
| 1 | [www.apple.com/airpods-pro/](https://www.apple.com/airpods-pro/) | three.js | 32.2× vh, 297 sections | 12 sticky layers (stack/pin effect); CSS scroll-driven animations (animation-timeline); 1 mix-blend-mode layer(s) |
| 2 | [igloo.inc](https://igloo.inc) | GSAP, ScrollTrigger, three.js | 1× vh, 0 sections | — |
| 3 | [lusion.co](https://lusion.co) | SplitText, three.js | 1× vh, 17 sections | WebGL hero; 1 mix-blend-mode layer(s) |
| 4 | [pudding.cool/2017/05/song-repetition/](https://pudding.cool/2017/05/song-repetition/) | — | 15.3× vh, 0 sections | — |
| 5 | [www.chess.com/](https://www.chess.com/) | Rive | 5× vh, 7 sections | looping video texture |
| 6 | [lichess.org/](https://lichess.org/) | — | 1.4× vh, 17 sections | — |

## 1. AirPods Pro 3 - Apple
- url: https://www.apple.com/airpods-pro/
- stack: three.js
- mechanics: 12 sticky layers (stack/pin effect); CSS scroll-driven animations (animation-timeline); 1 mix-blend-mode layer(s)
- page: 32.2× viewport tall · 297 sections · 0 canvas · 4 video
- type: display `"SF Pro Display", "SF Pro Icons", "Helvetica Neue", Helvetic 96px/600` (largest painted text: "The sound of science.") · body `"SF Pro Text", "SF Pro Icons", "Helvetica Neue", Helvetica, Arial, san` · @font-face: Apple Legacy Chevron, Apple Icons 100, Apple Icons 200, Apple Icons 300, Apple Icons 400, Apple Icons 500, Apple Icons 600, Apple Icons 700
- palette (most painted): #1d1d1f ×2198 · #333336 ×290 · #000000@0.80 ×221 · #000000@0.56 ×202 · #000000 ×183 · #6e6e73 ×170
- shots: `shots/www.apple.com_airpods-pro_-00.png` `shots/www.apple.com_airpods-pro_-01.png` `shots/www.apple.com_airpods-pro_-02.png`  ← look at the hero; look at the rest only if you take a steal from this one
- **steal:** apple.com/airpods-pro: one object owns the upper two-thirds and the headline is anchored low-left at the bottom of the first viewport. Here the object is a single gold king on our own board, lit from above; the headline sits in the dark the light does not reach.

## 2. Igloo Inc.
- url: https://igloo.inc
- stack: GSAP, ScrollTrigger, three.js  ·  built with Vue/Nuxt
- mechanics: —
- page: 1× viewport tall · 0 sections · 0 canvas · 0 video
- type: display `?` · body `"Times New Roman"` · @font-face: IBMPlexMono-Medium, IBMPlexMono-Regular
- palette (most painted): #000000 ×27 · #a0a5b1 ×2
- shots: `shots/igloo.inc-00.png` `shots/igloo.inc-01.png` `shots/igloo.inc-02.png`  ← look at the hero; look at the rest only if you take a steal from this one
- **steal:** NO USABLE CAPTURE: the headless browser got only the grey loading frame of its WebGL scene. Nothing is taken from it.

## 3. Lusion - Award Winning 3D and Interactive Web Studio
- url: https://lusion.co
- stack: SplitText, three.js
- mechanics: WebGL hero; 1 mix-blend-mode layer(s)
- page: 1× viewport tall · 17 sections · 3 canvas (WebGL live) · 0 video
- type: display `Aeonik 144px/400` (largest painted text: "Bold") · body `Aeonik` · @font-face: Aeonik, IBMPlexMono, LusionMono
- palette (most painted): #000000 ×1347 · #ffffff ×99 · #f0f1fa ×57 · #1a2ffb ×7 · #0016ec ×4 · #2b2e3a ×2
- shots: `shots/lusion.co-00.png` `shots/lusion.co-01.png` `shots/lusion.co-02.png`  ← look at the hero; look at the rest only if you take a steal from this one
- **steal:** not taken. Kept as the second-order receipt: black + saturated blue (#1a2ffb) on glossy 3D objects is what "tech studio" looks like right now, so our blue stays a thin data stroke and never fills a surface.

## 4. Are Pop Lyrics Getting More Repetitive?
- url: https://pudding.cool/2017/05/song-repetition/
- stack: nothing detected
- mechanics: —
- page: 15.3× viewport tall · 0 sections · 0 canvas · 0 video
- type: display `"Canela Web" 64px/400` (largest painted text: "Are Pop Lyrics Getting More ") · body `"PT Serif", Georgia, Times, serif` · @font-face: PT Serif, Roboto, Atlas Grotesk Web, Canela Web, Publico Text Web
- palette (most painted): #2a2a2a ×1711 · #000000 ×524 · #ffffff ×26 · #f3f3f3 ×15 · #440154 ×10 · #000000@0.90 ×8
- shots: `shots/pudding.cool_2017_05_song-repetition_-00.png` `shots/pudding.cool_2017_05_song-repetition_-01.png` `shots/pudding.cool_2017_05_song-repetition_-02.png`  ← look at the hero; look at the rest only if you take a steal from this one
- **steal:** pudding.cool: the graphic IS the argument, each step changes exactly one thing in it, and annotations sit on the data ("Avg. song", "Cheap Thrills") instead of in a legend. Beat 4: each ply's caption is written on the board at the destination square, from the recorded data, and nothing else changes in that step.

## 5. Chess.com - Play Chess Online - Free Games
- url: https://www.chess.com/
- stack: Rive
- mechanics: looping video texture
- page: 5× viewport tall · 7 sections · 0 canvas · 1 video
- type: display `"Chess Sans", sans-serif 42px/800` (largest painted text: "Play Chess Online on the #1 ") · body `-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, Helvetica, A` · @font-face: Chess Sans, Chess V3, Chess Note
- palette (most painted): #312e2b ×224 · #ffffff ×166 · #ffffff@0.50 ×95 · #ffffff@0.85 ×43 · #ffffff@0.72 ×30 · #4b4847 ×6
- shots: `shots/www.chess.com_-00.png` `shots/www.chess.com_-01.png` `shots/www.chess.com_-02.png`  ← look at the hero; look at the rest only if you take a steal from this one
- **steal:** not taken. First-order category receipt: flat green board, "#1 site" + "250+ million players" metric, one green CTA. We use no flat board, no audience metric, no green.

## 6. lichess.org • Free Online Chess
- url: https://lichess.org/
- stack: nothing detected
- mechanics: —
- page: 1.4× viewport tall · 17 sections · 0 canvas · 0 video
- type: display `Roboto, sans-serif 38px/300` (largest painted text: "1+0") · body `"Noto Sans", sans-serif` · @font-face: lichess
- palette (most painted): #4d4d4d ×212 · #000000 ×110 · #1b78d0 ×73 · #787878 ×40 · #ffffff ×31 · #5e5e5e (color(srgb 0.37 0.37 0.37)) ×30
- shots: `shots/lichess.org_-00.png` `shots/lichess.org_-01.png` `shots/lichess.org_-02.png`  ← look at the hero; look at the rest only if you take a steal from this one
- **steal:** not taken. First-order category receipt: a grid of time-control cards and live player counts. We use no card grid and no counts.
