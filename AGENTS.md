# Notes for contributors and coding agents

- Protocol: DeepPilot Bridge Protocol v2. The normative spec is `PROTOCOL.md` in
  [Mars-Sea/dsh-deeppilot](https://github.com/Mars-Sea/dsh-deeppilot); `core/.../protocol/Models.kt`
  mirrors it. Keep optional fields nullable or defaulted so older bridges still decode.
- `core/src/test/resources/protocol-fixtures/` holds copies of the cross-client fixtures from
  `tests/fixtures/` in dsh-deeppilot (the TypeScript Host and the iOS app test against the same
  files). Never edit them here: change them in the plugin repository, then run
  `scripts/sync-protocol-fixtures.sh`. `AuthVectorsTest` fails when a copy is stale.
- `core` is plain Kotlin/JVM and must stay free of Android imports. Put protocol, pairing,
  signing input, TLS pinning, reducers and anything else testable there, with tests.
- `app` holds Android-only code: Keystore keys, notifications, the foreground service, Compose UI.
  It is only configured when an Android SDK is present (see `settings.gradle.kts`).
- Never connect over `ws://` or `http://`. LAN hosts are trusted only by the pinned SPKI fingerprint.
- Never commit signing keystores, relay enroll keys or other secrets. Release signing reads env vars
  set by `.github/workflows/release.yml` from repository secrets.
- No aggregator push SDKs (JPush, Getui, …). Phase 2 adds vendor channels directly.
- User-facing strings go in `values/strings.xml` and `values-zh-rCN/strings.xml`.
- Checks: `./gradlew :core:test :app:assembleDebug`.
