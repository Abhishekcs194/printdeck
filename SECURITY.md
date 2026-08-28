# Security

## Reporting a vulnerability

Open a GitHub security advisory on this repository. Please don't file a public issue
for anything exploitable.

## Design

**Documents never leave the device.** Imposition, rendering and preview all happen
locally. There is no backend, no account and no telemetry. The only outbound traffic
PrintDesk ever makes is IPP to a printer on your own network.

### Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Speaking IPP to a printer. Nothing else. |
| `ACCESS_NETWORK_STATE` | Detecting whether a network is available before discovery. |

Deliberately **not** requested: `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, and
`MANAGE_EXTERNAL_STORAGE`. Files reach the app only through the Storage Access
Framework, the Photo Picker, or an explicit share — all of which are permissionless
and give the app access to exactly what the user chose and nothing more.

### Cleartext IPP

`network_security_config.xml` permits cleartext, which needs explaining.

Printing is a local-network protocol, and a great many printers only offer cleartext
IPP on port 631. Android's network security config can express "these domains" but not
"private subnets only", so the constraint is enforced in code instead:

1. `PrivateAddressGuard` rejects any host outside RFC1918, ULA and link-local ranges
   before a socket is opened. A public-IP print target is refused outright.
2. `ipps://` (IPP over TLS) is preferred wherever the printer advertises it, with
   trust-on-first-use certificate pinning held in Android Keystore-backed storage.
3. Falling back to cleartext requires an explicit, one-time acknowledgement.

### Data at rest

- Intermediate PDFs live in `cacheDir` and are purged on job completion and app exit.
- `allowBackup="false"` with restrictive `dataExtractionRules`: print history and
  cached documents are the user's papers and are never backed up or transferred.
- Release builds strip debug logging, so document names and paths never reach logcat.

### Build

- R8 full mode with resource shrinking.
- All dependency versions pinned in `gradle/libs.versions.toml`.
- Signing keys are never committed; release signing uses Play App Signing.
