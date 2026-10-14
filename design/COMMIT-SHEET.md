# COMMIT-SHEET — REXCHESS landing (`/` and `/engine`)

> Seven decisions made before the build. Checked again at verification. Source of the decisions: the approved
> Phase 0 plan (2026-10-09) and the answers that came with the approval.

## 1. Peak
Scene 3, "Inside the machine": the engine's real decision drawn **on the board itself**. Every legal move in the
recorded position appears as an arc from its piece. In the order the search rejected them, each arc is met by the
reply that refuted it and goes dark. One arc remains, in blue, and its line unrolls move by move. The data is the
root trace recorded from `Searcher` at a fixed depth. The board is cropped by the left edge of the viewport, and the
recorded numbers sit in the right third. Pinned for about 350 lvh, in stepped stages.

## 2. Color
- **Background:** `#050505`, oklch(0.115 0 0). Target mean frame lightness ≈ 0.16, raised only by the lit board.
  The page is dark because the concept is one light in a dark room: the darkness is the material the light cuts.
  This is house tell #1, kept on purpose (see field 7).
- **Text:** ivory `#F1E9D8`, oklch(0.936 0.024 86), 16.9:1. Dimmed ivory (60%) is 6.3:1.
- **Brand accent:** the app's gold `#c9a45c`, oklch(0.737 0.101 83), 8.7:1 on the background. Tier: restrained.
  Used on the wordmark and the primary action only. Solid, never a gradient (the app's gradient wordmark stays in
  the app).
- **Engine accent:** blue `#4F8CFF`, oklch(0.657 0.182 262), 6.3:1. Used only for engine data (arcs, the best line,
  the evaluation), as thin strokes and text, never as a filled surface or a glow.
- **Not cream, not lavender:** cream appears only as the marble squares under the light, never as a page surface.
  Lavender and purple-to-blue appear nowhere.
- **Not the category reflex:** the reflex is green or wood plus white, see field 6.

## 3. Type
- **Display:** Cormorant Garamond 500, plus 500 italic for the turn of a sentence.
- **Text:** Jost 400/500.
- **Engine numbers and lines only:** JetBrains Mono.
- **Contrast axis:** a calligraphic, high-contrast serif against a geometric sans.
- **Why not Inter:** it is the 2024–26 default, and Jost is already the app's text face, so the voice doesn't change
  when the board scales into `/game`.
- **Display ceiling:** clamp max 5.5rem.
- All three faces are OFL and self-hosted in the build, as Latin subsets with `font-display: swap`.

## 4. Grid break
The board is never centred and never fully inside the frame:
- **Hero:** the king stands in the right third and the board runs off the bottom edge.
- **Scene 3:** the board is cropped by the left edge (offset about −12vw) while the readout holds the right third.

The headline always sits in the part of the frame the light does not reach.

## 5. Motion budget
Three scroll-pattern families, nothing else scroll-triggered:
1. **scroll-scrub:** camera and pieces as a pure function of scroll progress (scenes 2 and 4).
2. **pinned-stage:** stepped stages inside one sticky section (scene 3).
3. **entrance-reveal:** one timed hero intro (at most 4 s, skippable), plus the finale lines.

Pins use CSS `position: sticky` sized in `svh`/`lvh`, driven by one in-house driver. If the driver grows past
~150 lines or needs sequencing, stop and propose GSAP. Only `transform` and `opacity` are animated.

## 6. Reflex check
- **a) First order**, from recon on 2026-10-09:
  - chess.com: a flat green board, a "#1 site" claim, a "250+ million players" metric and a green CTA.
  - lichess: a card grid of time controls, live player counts and a blue CTA.
  - The current REXCHESS landing: gold plus a feature-card grid.
- **b) Second order**, the "AI" escape from (a):
  - black with saturated electric blue on glossy 3D (Lusion: `#1a2ffb` on black and white),
  - glowing node-link search trees,
  - terminal readouts.
- **c) Ours:**
  - one physical board under one light;
  - the machine's thinking drawn as moves on that board, not as an abstract graph;
  - blue only as a thin stroke carrying data;
  - numbers only where the recording has them.

## 7. House tells broken
1. **The status bar** (logo · status · action) → no navigation bar on `/`. A small gold wordmark top-left and
   nothing else on the top edge; sign-in lives under the CTAs.
2. **Scroll-instruction footer / `01 / 05` counter** → none. The film starts moving when the visitor scrolls.
3. **Glow as depth** → no bloom, no emissive halos. Light is a real spotlight in the scene (directed mode), with
   falloff and a cast shadow. Until then, a page mask stands in (carried note in STORYBOARD).

Kept, with reasons:
- **#1 near-black:** the spotlight concept needs it.
- **#2 mono type:** used only for engine numbers and lines, never for labels or chrome.
- **#5 a warm metal accent:** the app's existing brand gold, kept by the owner's decision, not chosen from the
  palette's comfort zone.
