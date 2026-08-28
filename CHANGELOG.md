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
