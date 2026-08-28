# PrintDesk

**The desktop print dialog, on your phone.**

Android's print dialog gives you copies, colour, paper size and a page range. Every
genuinely useful control from a desktop print dialog is missing — N-up, booklet
imposition, page splitting, poster tiling, binding gutters, tray selection, duplex
edge, print quality, ink levels.

PrintDesk re-imposes your document **on the device** into the sheet layout you
actually want, then talks **directly to the printer over IPP** to drive the controls
Android hides.

Nothing is uploaded. There is no account, no backend and no telemetry — and because
this repo is public, that claim is checkable rather than a promise.

---

## What it does

### Layout

| | |
|---|---|
| **N-up** | 2, 4, 6, 9, 16 pages per sheet · reading order · gutters · cell borders · auto-rotate to fill the sheet |
| **Booklet** | Saddle-stitch imposition · signatures · creep compensation · right-to-left binding |
| **Split** | Cut each page into a grid — an A3 spread or a two-page scan becomes separate sheets |
| **Poster** | Blow one page across an N×M grid of sheets, with glue-flap overlap and assembly marks |
| **Pages** | `1-5,8,11-` · odd · even · reverse · reorder, rotate, delete |
| **Fit** | Fit to page · shrink oversized · exact % · actual size · margins · binding gutter · tray offset |

Imposition is **vector**. Pages are placed as PDF Form XObjects, so text stays sharp
and selectable. The common shortcut — render to bitmap, paste, re-encode — produces
fuzzy handouts and enormous files, and PrintDesk never does it. Rasterisation happens
only to draw the on-screen preview, and only from the already-imposed document, so the
preview cannot drift from what prints.

### Printing

PrintDesk speaks IPP directly, so it can ask the printer **what it can actually do**
and build the options screen from the answer — real trays, real duplex modes, real
quality levels, real ink levels. No hardcoded lists, no offering a tray that isn't
there.

Printers it can't reach directly fall back to Android's print dialog. The imposition
is already baked into the PDF by then, so nothing is lost either way.

---

## Building

Requires JDK 17 and the Android SDK (platform 37, build-tools 36.1.0).

```bash
./gradlew :app:assembleDebug     # build
./gradlew test                   # unit tests
./gradlew detekt                 # static analysis
```

`local.properties` needs `sdk.dir` pointing at your SDK.

## Layout

```
:app                 Compose screens, navigation, DI
:core:design         design tokens, theme, shared components
:core:model          domain types                          (pure JVM)
:pdf:imposition      layout maths — exhaustively tested     (pure JVM)
:pdf:engine          PdfBox execution + preview rendering
:print:ipp           IPP client + mDNS discovery
:print:system        Android print dialog fallback
```

`:pdf:imposition` is deliberately free of Android and of PdfBox. Booklet ordering,
creep, N-up geometry and range parsing are pure arithmetic, and that is exactly where
the subtle bugs live — so they are unit-tested in milliseconds instead of on paper.
The booklet tests **simulate folding the sheet** and assert the result reads 1, 2, 3…
for every page count from 1 to 64.

## Privacy and security

- **No storage permissions at all.** Files arrive via the Storage Access Framework,
  the Photo Picker, or a share intent. `MANAGE_EXTERNAL_STORAGE` is deliberately not
  declared.
- **`INTERNET` is used only to reach a printer.** A guard rejects any target outside
  private address ranges, so it cannot reach the public internet.
- IPPS with trust-on-first-use pinning where the printer supports it.
- No analytics, no crash reporting, no backup of your documents.

See [SECURITY.md](SECURITY.md).

## Licence

[Apache 2.0](LICENSE). Built on [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)
and [jipp](https://github.com/HPInc/jipp), both Apache 2.0.
