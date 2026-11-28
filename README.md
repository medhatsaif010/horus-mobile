# Horus Shield

Android security app by **Horus ICT** ([horusict.com](https://horusict.com)). It checks the phone on the phone itself: no computer, no root, and nothing about the user is sent anywhere.

## What it does

- **Phone scan:** known stalkerware (by package name and signing certificate), fake Egyptian bank and wallet apps, banking-trojan patterns (SMS + Accessibility, or drawing over other apps), hidden apps, device admins, notification access, apps installed from outside a store, root, security patch level and exploited vulnerabilities (CISA KEV), user-added CA certificates, proxy, Private DNS and screen lock.
- **Safety score** out of 100, "what changed since the last scan", and "who can see and hear you".
- **Link check:** inside the app, or from the share button in WhatsApp and Messages; warns when a site imitates a bank or wallet.
- **Bluetooth trackers:** finds AirTag, SmartTag, Tile and other trackers on a sonar-style radar, and tells when one may be moving with you.
- **Help:** a safe plan and Egyptian helplines for people who think they are being watched, and scam reporting.
- Arabic (Modern Standard and Egyptian) and English.
- Read-only by design: every fix button opens the right system setting, and the user decides.

## Build and release

Every push to `main` is built by GitHub Actions, tested on an emulator (`tools/smoke.sh`), and, if the test passes, signed with the official Horus ICT key and published:
`https://github.com/medhatsaif010/horus-mobile-releases/releases/download/apk/HorusShield.apk`

Pull requests get a debug preview build and emulator screenshots in a `preview-pr-N` release. Release builds refuse to finish unless the APK carries the official signing certificate:

```
SHA-256: 3F:67:B5:BF:64:96:25:FC:2D:F6:84:BD:CD:B3:49:DE:E0:7F:33:2A:F8:14:C7:CC:1E:4F:DF:E2:DC:44:B0:42
```

`app/horus-test.keystore` is a public test key for debug builds only.

## Threat intelligence

The threat pack is downloaded from `medhatsaif010/horus-intel` and accepted only when its RSA-SHA256 signature matches the public key built into `Intel.kt`.

## Credits

Stalkerware indicators: Echap (CC BY 4.0). C2 / URLhaus / MalwareBazaar: abuse.ch (CC0). Exploited vulnerabilities: CISA KEV.
