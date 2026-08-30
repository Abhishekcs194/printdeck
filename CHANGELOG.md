# Changelog

All notable changes to PrintDeck are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning is [semver](https://semver.org/).

## [Unreleased]

### Added
- Project scaffold: seven-module Gradle build (Kotlin 2.3.21, AGP 9.3.2, Gradle 9.7.1),
  Hilt, Compose, version catalogue, detekt, GitHub Actions CI.
- Design system in `:core:design` — semantic colour tokens with full light/dark
  palettes, a 14sp type scale, an 8dp radius scale, and the bordered `Section`
  container the app groups content with.
- Imposition engine in `:pdf:imposition`, pure JVM and Android-free:
  - **N-up** with configurable grid, reading order (including right-to-left),
    gutters, cell borders and auto-rotation to fill the sheet.
  - **Booklet** saddle-stitch imposition with signatures, creep compensation and
    right-to-left binding.
  - **Split** — cut each source page into a grid of output pages, with overlap.
  - **Poster** — tile one page across a grid of sheets with glue-flap overlap and
    assembly marks.
  - Page range parsing (`1-5,8,11-`, odd, even, reverse) and scaling/margin handling.
  - Automatic sheet-orientation selection, measured from how well the page fills a
    cell rather than guessed from the mode.
- 40 unit tests covering the above. The booklet suite simulates folding the sheet
  and asserts the booklet reads in document order for every page count from 1 to 64.
- Security posture: no storage permissions, `allowBackup=false`, R8 full mode,
  release log stripping, and `SECURITY.md` documenting the cleartext-IPP constraint.

- Layered printer discovery in `:print:ipp`, built for the case where a printer is
  on the network but the phone cannot see it:
  - **Widening rings** — remembered addresses, then mDNS plus a sweep of attached
    networks, then networks reachable only through a router. The wide ring
    escalates automatically when the nearby one finds nothing, because a user who
    cannot find their printer has no way to know a wider search is what they need.
  - **Probe before sweep** — one connection decides whether a network exists before
    spending 254 on it, so a wide search costs seconds rather than minutes.
  - **Subnet planning** from the device's own interfaces, its routing table, the
    conventional router address of each range, and the ranges consumer gear ships
    with. Remembered subnets are searched first, so the expensive path is paid once.
  - `PrivateAddressGuard` refuses any address outside RFC 1918 / link-local /
    carrier-NAT space before a socket opens, and rejects loopback so the client
    cannot be aimed at the phone itself.
  - **Diagnosis when nothing is found** — instead of a bare "no printers found",
    the search reports why: no network, or a router answering on a segment this
    device is not part of (stated plainly, including that searching longer will
    not help, because NAT blocks inbound traffic). Causes that cannot be proven
    from the device, such as access-point client isolation, are offered as things
    to check rather than asserted as the answer.
  - 35 unit tests over the address arithmetic, the guard's boundaries, the
    planner's ordering and caps, and the diagnosis decision tree.

- `:pdf:engine` — executes an imposition plan against a real document with
  PdfBox-Android, producing the PDF that gets printed.
  - Pages are placed as **vector Form XObjects**, never rasterised, so text stays
    selectable and files stay small (an 8-page 2-up sample is 5 KB). A test asserts
    the output contains form objects and no images, so a future "simplification"
    into bitmap pasting fails the build.
  - A source page used on many sheets is imported once, so poster tiling does not
    multiply file size by the tile count.
  - Page `/Rotate` is folded into the reported page size, so scanned documents are
    not laid out sideways.
  - Spills to a scratch file past 32 MB rather than risking the heap on a phone.
  - `SampleOutputGenerator` writes one real PDF per layout mode to `build/samples/`
    for hands-on checking — a booklet's fold order is not something an assertion
    can confirm.

- Documents screen — open a PDF or images through the system picker or the photo
  picker, or receive one shared from another app. Session recents, empty state,
  and inline error reporting.
- Layout editor — live sheet preview with per-mode controls for N-up, booklet,
  split and poster, plus paper size, margins and binding gutter.
  - The preview is a render of the imposed PDF itself, so it cannot disagree with
    what prints. Painting a mock layout in Compose would be a second
    implementation of the imposition rules, and second implementations drift.
  - Only the visible sheet is imposed, so preview cost is constant whether the
    document is four pages or four hundred.
- Images are converted to PDF at the door, in `:pdf:engine`, so photos take the
  same imposition path as documents and nothing downstream learns a second format.
- Design system: buttons, segmented tabs, choice chips, stepper, option rows,
  empty state, inline notice, pills and status dots — plus 22 Phosphor icons (MIT)
  bundled as vector drawables.
- Headless screenshot rendering writes real PNGs of each screen to
  `build/screenshots/`, so the interface can be reviewed without a device.

- Preview sheets can be swiped through, with neighbours prefetched so a swipe
  lands on a drawn sheet rather than a blank one.
- Settings fold away behind a handle, giving the preview the whole screen. On a
  phone the controls otherwise crowd out the thing they are adjusting.

- Opening a document now shows it one page per sheet, unchanged. Re-laying out a
  document is a choice the user makes, not something already done to it by the
  time they first look.

- Pinch to zoom the preview, up to 8x, with one-finger pan once zoomed and
  double-tap to zoom to a point or back out. Useful for checking whether 9-up
  will still be readable before committing paper to it.

- The settings panel takes a larger share of the screen when open, sized as a
  fraction of screen height rather than a fixed value, so the split holds on any
  device.

- Printing works. The Print button imposes the whole document and hands it to the
  platform print dialog, which reaches any printer the phone can already see
  through Mopria, and offers "Save as PDF" when there is none.
  - Chosen paper size and orientation are passed through as starting attributes,
    so a document imposed for A4 landscape is not silently rescaled onto portrait
    Letter.
  - Colour mode is left to the dialog rather than forced, since which mode is
    right depends on which cartridge the printer currently has ink in.

### Fixed
- Pinch zoom no longer recomposes the pager on every touch event. Scale and offset
  are read inside `graphicsLayer`, so a gesture re-runs only the draw phase.
- Rendered sheets are no longer cached without bound; only the visible sheet and
  its neighbours are kept. A long document previously accumulated every sheet
  swiped past, at several megabytes each.
- Preview sheets render at a resolution meant for zooming rather than for a
  thumbnail, so pinching in shows detail rather than enlarged mush.
- Double tap animates rather than jumping.
- Reading order is no longer offered on a single-row or single-column grid, where
  "across" and "down" describe the same traversal and the control could not change
  anything. A setting that visibly does nothing reads as a broken app.
- Changing reading order no longer silently discards the right-to-left setting;
  the two axes are now independent, and right-to-left is exposed for N-up.
- Segmented tab labels no longer wrap and clip against the fixed segment height.

### Changed
- Renamed from PrintDesk to **PrintDeck**. printdesk.io is an established
  print-shop-management product, so the original name would have been outranked
  in search by an adjacent commercial product — defeating the point of choosing a
  findable name — and left the Play listing open to a name complaint.

### Notes
- `compileSdk` is 37 while `targetSdk` is 36. AndroidX 1.19 and Compose 2026.08
  require compiling against API 37; targetSdk 36 is what Google Play mandates from
  31 August 2026 and is what governs runtime behaviour.
- AGP 9 is required rather than preferred: Hilt's Gradle plugin refuses to apply on
  AGP 8.x as of 2.59.
