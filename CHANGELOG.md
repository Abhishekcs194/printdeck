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

- Ink choice: colour or black and white, set in the app rather than left to the
  print dialog. The preview desaturates to match, so it never shows colour for a
  job that will print black and white.

- **Discovery is now wired into the app.** A Printers screen runs the widening
  search, confirms each result by asking it over IPP, and shows what the printer
  actually reports: model, state, ink levels, duplex support, print qualities and
  media types — including options the platform print dialog has no way to express.
- Printers found are remembered, along with the subnet they were on, so the next
  search checks the likely places first instead of paying for the widest search
  every time.
- Printers can be added by address, for networks the phone cannot search.
- When nothing is found, the diagnosis is shown rather than a bare empty list.

- **Printing over IPP, from PrintDeck's own options screen.** Every control is
  built from what the printer reported, so it can only offer real choices — no
  duplex on a simplex machine, no glossy on a printer with one paper type. Print
  quality and media type appear here for the first time; neither can be expressed
  through Android's print framework at all.
- Documents are rasterised on the device when the printer cannot take PDF, which
  is most consumer inkjets.
- Job progress is followed until the printer says it is finished.
- Android's print dialog is kept as an explicit fallback, since it reaches
  printers that do not speak IPP and offers Save as PDF.

### Fixed
- A landscape two-up job printed shrunk into the top half of an upright sheet.
  The earlier attempt at this used jipp-pdl's `RenderablePage.rotated()`, which
  is a *half* turn for the reverse of a duplex sheet and leaves page dimensions
  unchanged — it was shipped and changed nothing. The quarter turn now happens
  during rasterisation, where it folds into the transform the page is already
  drawn through, and seven tests check the page fills the turned raster corner to
  corner rather than merely fitting inside it.
- Paper type showed a selection that was never sent. The chip fell back to the
  printer's first entry for display while the job carried no paper type at all.
- Paper types are named as people name paper — "Plain paper" rather than IPP's
  "stationery", and Canon's keywords as they appear on the packet rather than
  mechanically trimmed to "Semisuper". Plain paper sorts first and is the default.
- Choosing a printer returned to a screen still saying no printer was chosen. The
  print screen sampled the selection once at construction, but it stays on the
  back stack while the picker is used, so it never saw the choice.
- Options carried over from a previous printer are dropped if the new one does
  not support them, rather than being sent and risking a rejected job.
- Jobs now tell the printer not to rescale them (`print-scaling: none`). Printers
  default to `auto` and are otherwise free to resize a sheet whose page positions
  were computed to the millimetre.
- Margins default to 5mm rather than none, keeping content out of the border a
  printer physically cannot image. Zero is still available for edge-to-edge.
- A landscape imposition printed shrunk into the middle of a portrait sheet, with
  white bands top and bottom. The raster is now rotated to match the way paper
  feeds, which is the correction a desktop print driver makes silently.
- A remembered printer was reported as found without ever being contacted. The
  check probed its subnet's gateway rather than the printer, and a router answers
  on its own subnet from neighbouring networks — so a phone that had changed
  network listed a printer it could not reach, and skipped the wider search that
  would have found it properly.
- Discovery now restarts when the device changes network, and clears results
  found somewhere else. Switching between the bands of one router is enough to
  change subnet, because those are separate access points.
- The printers screen could report "1 printer found" above an empty list. A device
  that answered on a printer port but not to IPP was removed from the list while
  still being counted by the diagnosis. Such devices are now shown, with the
  reason and a retry, and the diagnosis only speaks when there is genuinely
  nothing on screen to act on.
- A network sweep never completed, so the printer search ran forever and never
  reported what it had found. The flow was held open by an `awaitClose` that had
  nothing to wait for.
- A printer answering on several ports at once was listed several times. Results
  are now keyed by address, and the IPP port wins because it is the only one that
  can report capabilities.
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
