# STORYBOARD — REXCHESS landing (`/`)

## Film meta
- **Product:** REXCHESS, a chess app with its own engine. Bitboard alpha-beta (PVS) search, and a neural-network
  evaluation trained on Lichess games.
- **Audience:** people who might play the engine, and recruiters looking at a portfolio project. Mostly on a
  phone, mostly English-reading.
- **The one feeling:** precision: the hush before a move that has been calculated.
- **Peak scene:** 3, "Inside the machine" (intensity 9).
- **Assets:** the story is rendered from Poly Haven's "Chess Set" by Riley Queen (CC0, marble, real-world scale),
  lit by one SpotLight, with the CC0 HDRI `studio_small_03` for reflections only. The owner rejected the app's
  procedural pieces for the landing ("real images, not drawn pieces", 2026-10-09). Renderer: `design/render/scene.html`;
  masters in `design/sourced/` (git-ignored, ledger `ASSETS-SOURCED.md`). No generated or stock imagery.
  `/game` keeps the app's own board, so beat 5 changes sets. To make that handoff seamless, add this set to the app
  as a 3D look.
- **Sourced-asset findings (Poly Haven set):**
  - Every piece is a separate named node (`piece_king_white`, `piece_knight_black_01`, …) and squares are 5.8 cm,
    so any engine position can be set exactly.
  - The model's own layout is mirrored: seen from its white side, a1 is light and the king stands on d1. The board's
    colours are right only with White on the model's −x side, so `scene.html` maps squares that way and turns every
    piece 90°.
- **Sourced-asset findings (the app's scene, used for the first mockup):**
  - The scene renders on a real GPU in headless Chrome (`channel: 'chrome'`, ANGLE D3D11), so stills can be
    captured from it reproducibly.
  - The board group is pitched toward the scene's own camera (`BASE_PITCH`). Any other camera has to level it first,
    or the board reads as a Dutch angle.
  - Rank and file labels are sprites and can be hidden per shot.
- **Assumptions made:** none left open. The owner answered every Phase 0 question on 2026-10-09: name REXCHESS,
  English, demo login, beat 5 expands into `/game/:id`, root trace, prerender, sticky + driver, gold kept.
- **References taken** (design/refs/REFERENCES.md):
  - **apple.com/airpods-pro:** one object owns the upper two-thirds, headline anchored low-left. Changed here: the
    object is the king under its own light, and the headline sits where the light doesn't reach.
  - **pudding.cool (song repetition):** each step changes one thing in the graphic, and annotations sit on the data.
    Changed here: in scene 4 the captions are written on the board at the destination square.
- **Moodboard read:** near-black ground and one lit object, with the colour carried by the material (marble, gold)
  under a single hard light from above. Avoid neon on sculpture, the gold-ring luxury template, and brown studio
  backdrops.
- **Style gate verdict:** *pending owner*, on the canvas https://claude.ai/artifact/5HSYNifZH4oJiDJK22a7gx
  (hero and finale, desktop and phone).
  - The light pool is now a real spotlight in the scene. The page adds only a soft vignette so the copy stays
    readable.
  - Carried note: the mockup loads fonts from Google; the build self-hosts them.

## Arc

| # | Scene | Beat | Intensity | layout family | motion family |
|---|-------|------|-----------|---------------|---------------|
| 1 | The silence | hook | 6 | full-bleed-media | entrance-reveal |
| 2 | The instinct | rising | 5 | pinned-canvas | scroll-scrub |
| 3 | Inside the machine | **peak** | 9 | split-asymmetric | pinned-stage |
| 4 | Consequences | proof | 6 | marginal-notes | scroll-scrub |
| 5 | Your game | door | 5 | split-asymmetric | none |
| 6 | Your move | door | 4 | centred-type | entrance-reveal |

---

### Scene 1 — The silence   | beat: hook | intensity: 6
- **purpose:** feel: the hush, one piece alone under a light / learn: this is chess, and it is serious
- **subject:** a single gold king on e4
- **layout_family / motion_family:** full-bleed-media / entrance-reveal
- **camera:** low angle, close, the king in the right third, board receding into the dark; slow push-in during the intro
- **lighting:** hard contrast, one source from directly above, fast falloff
- **motion:** timed, at most 4 s:
  1. The board rises out of black (opacity).
  2. The light pool widens (mask scale).
  3. The still eases from scale 1.04 to 1.0.
  4. "64 squares." then "Infinite possibilities." then the headline, then the CTAs.

  Any click, key or scroll skips to the end. A session flag skips it for returning visitors. No WebGL in this scene.
- **transition_in / out:** cut (page load) / depth-parallax: the still cross-fades into the live canvas at the same
  camera pose
- **scroll_len:** 100svh
- **copy:** "64 squares. Infinite possibilities." / H1: "Don't just play chess. *Outsmart the board.*" / CTAs
  "Play now" → `/#play`, "Explore the engine" → `/engine` / "Have an account? Sign in"
- **media:**
  - type: still (AVIF/WebP, 390 and 1440 widths), captured from LuxeBoardScene
  - route: own scene, captured with Playwright
  - direction: subject + low close camera + one hard top light + `#050505` ground + marble-gold look
- **fallback:** the same still and copy, fully visible from first paint (prerendered). Reduced motion: no push-in,
  no sequencing.

### Scene 2 — The instinct   | beat: rising | intensity: 5
- **purpose:** feel: the pull of an obvious move / learn: a human sees one move
- **subject:** the knight from the recorded position, and the human move it makes
- **layout_family / motion_family:** pinned-canvas / scroll-scrub
- **camera:** macro on the knight, slow push as it lifts; the board edges fall out of focus into the dark
- **lighting:** hard contrast; the light follows the knight
- **motion:** scrubbed by scroll:
  - the knight lifts and its shadow shrinks and softens;
  - it travels and sets down;
  - origin and destination squares are marked.

  Reversible: the pose is a function of progress.
- **transition_in / out:** depth-parallax (from the still) / shutter: the light drops before scene 3 raises it again
- **scroll_len:** 250lvh (≈150lvh on phones)
- **copy:** "You see a move." … "The machine sees thousands."
- **media:** live 3D (directed mode), lazy-loaded on scroll intent. Direction: macro, one hard light tracking the
  knight, marble-gold look.
- **fallback:** two stills (knight lifted, knight set down) and the two lines. Used for reduced motion, for no
  WebGL (`failIfMajorPerformanceCaveat`), and for slow devices.

### Scene 3 — Inside the machine   | beat: PEAK | intensity: 9
- **purpose:** feel: vertigo, then calm, as everything but one line goes dark / learn: the engine weighed every
  move, and refuted the human one
- **subject:** the recorded root trace drawn on the board
- **layout_family / motion_family:** split-asymmetric (board cropped by the left edge, readout in the right third) /
  pinned-stage
- **camera:** high angle, nearly top-down, so arcs read as moves
- **lighting:** the room dims; the arcs are thin strokes that light nothing; blue only on the surviving line
- **motion:** stepped stages, scrubbed:
  1. Every legal move fans out as an arc.
  2. Rejected arcs are each met by their refuting reply, in trace order, and go dark. The knight move from
     scene 2 is one of them, and its refutation is shown.
  3. The surviving arc turns blue and its line unrolls.
  4. The readout settles.
- **transition_in / out:** shutter / cut to the replay angle
- **scroll_len:** 350lvh (≈210lvh on phones)
- **copy:** captions computed from the trace only, e.g. "34 legal moves." / "33 refuted." / "One remains." Readout
  (JetBrains Mono): best move, evaluation, depth N, "{nodes} positions searched (nodes) at depth N". Label: "Recorded
  from the REXCHESS engine, depth N."
- **media:** live 3D (directed mode) plus showcase.json (root trace). Direction: high angle, dim room, thin arcs.
- **fallback:** a still of the final stage (the blue line on the board) with the same captions and readout as text.

### Scene 4 — Consequences   | beat: proof | intensity: 6
- **purpose:** feel: every move pays off / learn: the best line has a visible consequence
- **subject:** 4–6 recorded plies of the best line
- **layout_family / motion_family:** marginal-notes / scroll-scrub
- **camera:** three-quarter view, the whole board in frame
- **lighting:** the light returns, even across the board
- **motion:** scrubbed. Each ply: the piece glides, its origin and destination are marked, and a caption is written
  at the destination square. The engine's reply follows.
- **transition_in / out:** cut / release: the pin ends and the board settles into the start position
- **scroll_len:** 300lvh (≈180lvh on phones)
- **copy:** captions from recorded fields only: capture, check, evaluation change (e.g. "Nxe5. Takes a pawn.
  +0.4 → +1.6").
- **media:** live 3D plus showcase.json. Direction: three-quarter, even light.
- **fallback:** a still of the final position, with the plies as an ordered list of captions.

### Scene 5 — Your game   | beat: door | intensity: 5
- **purpose:** feel: invited / learn: one click starts a real game against this engine
- **subject:** the board in the starting position, in the app's own look, next to its controls
- **layout_family / motion_family:** split-asymmetric (board left, controls right; stacked on phones) / none
- **camera:** the game page's own camera, so the board that scales into `/game/:id` is the same board
- **lighting:** the app's studio light
- **motion:** none on scroll. On "Start game": a view transition scales the board into `/game/:id`, with a plain fade
  where view transitions are unsupported.
- **transition_in / out:** release / view-transition
- **scroll_len:** content
- **copy:** "Play the engine. Then see how it thought."
  - Controls: level (preselected), colour (preselected), "Start game".
  - Below: "Signed in? You'll play as yourself."
  - States: "Starting…", "Engine busy, try again in a moment", "Can't reach the engine" + Retry.
- **media:** HTML controls plus a poster still of the start position. The live scene is optional, never required.
- **fallback:** this is the fallback. It is reachable at `/#play` immediately, and works without WebGL, without the
  story scene and with reduced motion.

### Scene 6 — Your move   | beat: door | intensity: 4
- **purpose:** feel: the quiet after the film / learn: the move is the visitor's
- **subject:** a single king again, in a wider frame
- **layout_family / motion_family:** centred-type / entrance-reveal
- **camera:** wide, high, the king small in the dark (moodboard tile 06)
- **lighting:** one cone from above
- **motion:** on entering: the lines reveal (opacity + 8px rise), then the wordmark.
- **transition_in / out:** cut / none (end of page)
- **scroll_len:** 100svh
- **copy:** "Every move has a consequence." / "What's yours?" / REXCHESS wordmark (gold) / "Play now →" → `/#play`
  / "Explore the engine" → `/engine`
- **media:** a still captured from the scene
- **fallback:** the same, static

## Gate 0 checklist
- [x] exactly one scene with intensity ≥8 (scene 3)
- [x] no two adjacent scenes share a layout family or a motion family
- [x] 3 motion families across the page (entrance-reveal, scroll-scrub, pinned-stage)
- [x] every scene has real copy and a fallback
- [x] every `media:` block is filled (own scene: stills or live 3D, with direction)
- [ ] storyboard approved by the owner
