# Fonts

The app's two typefaces, served from the site itself rather than from Google Fonts: one connection
less before the first text can be drawn, and nothing sent to a third party. Declared in
`src/styles.scss`, which the build turns into hashed files under `media/` (cached for a year).

| File | Typeface | Used for |
|---|---|---|
| `jost-latin-wght-normal.woff2` | [Jost](https://github.com/indestructible-type/Jost), variable weight 100–900 | `--font`: the interface |
| `cormorant-garamond-latin-wght-normal.woff2` | [Cormorant Garamond](https://github.com/CatharsisFonts/Cormorant), variable weight 300–700 | `--serif`: titles |
| `cormorant-garamond-latin-wght-italic.woff2` | the same, italic | `<em>` in titles |

Latin subset only: it covers French, œ included. Taken from the
[Fontsource](https://fontsource.org) packages `@fontsource-variable/jost` and
`@fontsource-variable/cormorant-garamond` 5.3.0. Both typefaces are under the SIL Open Font License
1.1 (`OFL.txt`).
