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
