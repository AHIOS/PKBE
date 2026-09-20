# PKBE Blueprint — Proximity Passkey Activation POC

Living specification. If this file conflicts with `proximity_passkey_activation_protocol.md` or `passkey_activation_blueprint.md`, **this file wins**.

Phase 1 scope: Spring Boot relying party + thin iOS and Android apps. Same REST; OS-native passkey APIs.

---

## 1. Goal

Move app activation from an already-active device (**A**) to a newly logged-in device (**B**) by proving **physical proximity** with WebAuthn / CTAP 2.2 **hybrid (caBLE)** as implemented by Apple and Google.

Business logic outside passkeys (real login, KYC, banking activation) already exists elsewhere. This POC only:

1. Stub-login a user.
2. Enroll a platform passkey on the first device and mark it `ACTIVE`.
3. On a second device, enroll a **pending** platform passkey, then have A sign a hybrid assertion.
4. Atomically promote B and revoke A. One active device at a time.

We do **not** implement custom BLE, custom QR payloads, FCM, SSE, SOAP, or an SDK.

---

## 2. Locked decisions

| Topic | Decision |
| --- | --- |
| Clients | Mobile. iOS (`AuthenticationServices`) and Android (Credential Manager). |
| Web demo | Embedded `/webview/` page for comparing Safari/WKWebView vs native sheets. RP is JSON + well-known files + that page. |
| Credentials | One handover passkey per user (logical local-only). No second cloud-synced passkey. |
| Backend | Spring Boot, in-memory stores, Yubico `webauthn-server-core`. |
| Transport | OS-native hybrid / caBLE. App never opens a GATT socket. |
| Who POSTs verify | Device **B** (the WebAuthn client), after `get()` returns. |
| Notifications | None. `get()` on B blocks until A finishes. |

---

## 3. Roles

```
iPhone B (client)  --HTTPS-->  Spring RP
       |                         ^
       | OS hybrid QR + BLE      |
       v                         |
iPhone A (authenticator) --------+  (A does not POST verify)
```

- **Server:** RP ID, challenges, signature verify, single-active-device state.
- **Device B:** WebAuthn **client**. Shows the system FIDO QR.
- **Device A:** WebAuthn **authenticator**. Scans QR, BLE, Face ID, signs.
- **OS:** Owns CTAP 2.2 hybrid. Apple/Google may add a cloud-assist channel; we do not implement it.

Device A does not need this app in the foreground to scan. The passkey lives in the platform password manager. The app is required on A for **enrollment**.

---

## 4. Why this is not the old blueprint

The earlier docs disagree with the platform.

- Device B is the client. The assertion is delivered to B over hybrid, then B sends it to the server.
- Do not have A call `/activation/verify`.
- Do not use FCM/SSE to push a result to B.
- Do not mint a custom QR with BLE pairing keys. The OS sheet **is** the QR.
- Third-party apps cannot speak raw CTAP2 on BLE. Only `AuthenticationServices` / Credential Manager.

---

## 5. Protocol

### 5.1 Stub identity

`POST /v1/login` `{ "username": "alice" }` returns a bearer token. No password. Real IdP stays outside this POC.

All other `/v1/*` routes require `Authorization: Bearer <token>`.

`deviceId` is a client-generated UUID, persisted on the device (Keychain / UserDefaults). Sent in register/handover bodies and as `X-Device-Id` on `GET /v1/me`.

### 5.2 Device states

Per user, at most one `ACTIVE` credential and at most one `PENDING` credential.

```
NONE --> PENDING_ENROLL --> ACTIVE
ACTIVE(A) + PENDING(B) --handover verify--> ACTIVE(B) + REVOKED(A)
```

Revoked credentials are dropped from the assertion-eligible set.

### 5.3 Ceremony 1 — first-device enroll

When the user has no `ACTIVE` credential:

1. B (or A — first phone) `POST /v1/register/options` `{ "deviceId" }`.
2. Server returns WebAuthn **create** options:
   - `authenticatorAttachment: platform`
   - `residentKey: required` / `requireResidentKey: true`
   - `userVerification: required`
   - `timeout: 120000`
   - `attestation: none`
3. iOS `ASAuthorizationPlatformPublicKeyCredentialProvider` creates the passkey.
4. `POST /v1/register/verify` with the standard PublicKeyCredential JSON.
5. Server stores credentialId, COSE key, signCount, AAGUID, BE/BS. Status = **ACTIVE**.

If an `ACTIVE` credential already exists and `deviceId` is a **different** device, the new credential is stored as **PENDING** (not active). Same `deviceId` that is already `ACTIVE` is rejected (`ALREADY_ACTIVE`). Re-enrolling `PENDING` on the same `deviceId` replaces the pending credential.

### 5.4 Ceremony 2 — handover

B is logged in as the same username and is not the active device.

1. B completes Ceremony 1-style register; server keeps it `PENDING`.
2. `POST /v1/handover/options` `{ "deviceId" }`.
3. Preconditions: this `deviceId` has `PENDING`; some other device is `ACTIVE`.
4. Server `startAssertion` with:
   - random 32-byte challenge, TTL 120s
   - session binding `{ userId, deviceId_B, pendingCredId_B }`
   - `allowCredentials` = **only** the ACTIVE credential
   - `userVerification: required`
   - `hints: ["hybrid"]`
   - timeout 120s
5. B presents `ASAuthorizationController` (platform + security-key assertion requests, **not** `preferImmediatelyAvailableCredentials`). OS shows FIDO QR when the ACTIVE cred is not local.
6. User scans with A (different Apple ID). caBLE + Face ID on A. Assertion returns to B.
7. `POST /v1/handover/verify` with the assertion JSON.
8. Server checks TTL, challenge, origin, RP ID, UV, credentialId == ACTIVE, signature.
9. Atomic swap: PENDING(B) → ACTIVE; ACTIVE(A) → REVOKED; challenge invalidated.

If there is no PENDING cred for B, handover verify fails and A stays active (no lockout).

### 5.5 Challenge binding

Wire format is a normal WebAuthn challenge (32 random bytes). Binding of `deviceId` + pending credential id lives in the **server ceremony store**, not in a custom hash on the wire.

---

## 6. Local-only vs iCloud sync

Relying parties cannot tell iCloud Keychain “do not sync”. Consumer Apple passkeys are usually backup-eligible (`BE=1`). If A and B share an Apple ID, B may get a local copy and skip BLE.

**Phase 1 demo lab:** two iPhones, **different Apple IDs**. Then B cannot see A’s credential and iOS must offer hybrid.

Mitigations still applied:

- `allowCredentials` = A’s credential only.
- `hints: ["hybrid"]` on handover options.
- iOS handover does not prefer immediately-available credentials.
- `GET /v1/me` surfaces BE/BS.
- `pkbe.require-device-bound` (default `false`) rejects `BE=1` at registration when set.

Phase 2 (Android) is the stronger cross-ecosystem proof: iCloud and Google Password Manager do not share keys.

Do not add a second synced passkey in this POC.

---

## 7. HTTP API

Base URL for the iOS app must be `https://<rp-id>/` (Associated Domains / WebAuthn origin). HTTP is only for local JVM tests.

### `POST /v1/login`

```json
{ "username": "alice" }
```

```json
{ "token": "<opaque>", "username": "alice" }
```

### `POST /v1/logout`

Invalidates the bearer token.

### `GET /v1/me`

Header `X-Device-Id: <uuid>`.

```json
{
  "username": "alice",
  "thisDeviceId": "…",
  "thisDeviceStatus": "NONE | PENDING | ACTIVE",
  "activeDeviceId": "… or null",
  "pendingDeviceId": "… or null",
  "thisDeviceCredentialId": "base64url credential id for THIS device, or null",
  "activeCredential": {
    "credentialIdPrefix": "abcd",
    "aaguid": "…",
    "backupEligible": true,
    "backupState": true
  }
}
```

`activeCredential` is the **server registry** row for the active device. It is independent of the Passwords app until the client reconciles.

### `POST /v1/unenroll`

```json
{ "deviceId": "…" }
```

Clears ACTIVE and/or PENDING enrollment for this `deviceId` only (e.g. user deleted the passkey in Passwords). Other devices are untouched. Returns an updated `GET /v1/me` body.

### `POST /v1/register/options`

```json
{ "deviceId": "…" }
```

Body is Yubico `PublicKeyCredentialCreationOptions` JSON (base64url binaries), suitable for the iOS wrapper.

### `POST /v1/register/verify`

```json
{ "deviceId": "…", "credential": { "id": "…", "rawId": "…", "type": "public-key", "response": { "attestationObject": "…", "clientDataJSON": "…" } } }
```

### `POST /v1/handover/options`

```json
{ "deviceId": "…" }
```

Body is Yubico assertion request JSON (`challenge`, `rpId`, `allowCredentials`, `hints`, …).

### `POST /v1/handover/verify`

```json
{ "deviceId": "…", "credential": { "id": "…", "type": "public-key", "response": { "authenticatorData": "…", "clientDataJSON": "…", "signature": "…", "userHandle": "…" } } }
```

### Errors

```json
{ "error": "ALREADY_ACTIVE", "message": "…" }
```

Codes: `UNAUTHORIZED`, `ALREADY_ACTIVE`, `NO_ACTIVE_DEVICE`, `NO_PENDING_ENROLL`, `CHALLENGE_EXPIRED`, `DEVICE_BOUND_REQUIRED`, `WEBAUTHN_FAILED`, `BAD_REQUEST`.

### Well-known (phase 1)

Served by Spring controllers (not static files). Content comes entirely from env/config.

`GET /.well-known/apple-app-site-association`

- HTTP 200, `Content-Type: application/json`, **no redirect**.
- `webcredentials.apps` from `PKBE_AASA_APPS` or `PKBE_APPLE_TEAM_ID` + `PKBE_IOS_BUNDLE_ID`.

`GET /.well-known/assetlinks.json`

- Always present. Empty array until `PKBE_ANDROID_PACKAGE_NAME` + `PKBE_ANDROID_SHA256_FINGERPRINTS` are set (phase 2).

`GET /v1/public-config` (no auth) returns the resolved `rpId`, `publicBaseUrl`, origins, bundle ids.

---

## 8. Configuration

All knobs are env-overridable (Render dashboard, tunnel shell, or `.env`).

```yaml
server:
  port: ${PORT:8080}

pkbe:
  public-base-url: ${PKBE_PUBLIC_BASE_URL:}   # https://host — derives rp-id + origins
  rp-id: ${PKBE_RP_ID:}
  rp-name: ${PKBE_RP_NAME:PKBE}
  origins: ${PKBE_ORIGINS:}                   # comma-separated
  apple-team-id: ${PKBE_APPLE_TEAM_ID:TEAMID}
  ios-bundle-id: ${PKBE_IOS_BUNDLE_ID:com.uci.pkbe}
  aasa-apps: ${PKBE_AASA_APPS:}
  android-package-name: ${PKBE_ANDROID_PACKAGE_NAME:}
  android-sha256-fingerprints: ${PKBE_ANDROID_SHA256_FINGERPRINTS:}
  require-device-bound: ${PKBE_REQUIRE_DEVICE_BOUND:false}
  challenge-ttl-seconds: ${PKBE_CHALLENGE_TTL_SECONDS:120}
```

**Render / tunnel minimum:** set `PKBE_PUBLIC_BASE_URL=https://YOUR_HOST` and `PKBE_APPLE_TEAM_ID`. RP ID and origins are derived from the URL host.

See [server/.env.example](server/.env.example), [render.yaml](render.yaml), and [README.md](README.md).

iOS reads `PKBERpId` / `PKBEBaseURL` from Info.plist (xcconfigs under `ios/Configs/`).

---

## 9. iOS app

Path: `ios/`. SwiftUI. Bundle id configurable (`PKBE_IOS_BUNDLE_ID` on server; Xcode `PRODUCT_BUNDLE_IDENTIFIER`).

- Associated Domains: `webcredentials:$(PKBE_RP_ID)` from xcconfig
- Registration: `ASAuthorizationPlatformPublicKeyCredentialProvider`
- Handover: platform **and** security-key assertion requests, same challenge / allowCredentials
- Persist `deviceId` and bearer token
- Screens: login, status, enroll, activate via nearby device

Use `Configs/Local.xcconfig`, `Tunnel.xcconfig`, or `Render.xcconfig` so URL and RP ID stay in sync with the server. Set a real Apple Team ID in server env before device testing.


---

## 10. Android app

Path: `android/`. Jetpack Compose. Application id `com.uci.pkbe`.

- Digital Asset Links: `/.well-known/assetlinks.json` from `PKBE_ANDROID_PACKAGE_NAME` + `PKBE_ANDROID_SHA256_FINGERPRINTS`
- Registration: `CreatePublicKeyCredentialRequest` with `preferImmediatelyAvailableCredentials = true`
- Handover: `GetCredentialRequest` with `preferImmediatelyAvailableCredentials = false` (system can show hybrid QR)
- Same REST as iOS. Native + WebView tabs.

Product flavors: `local`, `tunnel`, `render`.

---

## 11. Verification (phase 1)

1. AASA at `https://<rp-id>/.well-known/apple-app-site-association` → 200 JSON, no redirect.
2. Asset Links at `https://<rp-id>/.well-known/assetlinks.json` when Android is in use.
3. Phone A: login, enroll, `thisDeviceStatus=ACTIVE`.
4. Phone B, **different account**, same username: pending enroll, handover, system QR, scan with A, B becomes ACTIVE, A revoked.
5. Replay assertion → fail. Expired challenge → fail. Never two ACTIVE devices.

Hybrid cannot be proven in Simulator / typical emulators.

---

## 12. Layout

```
PKBE/
  BLUEPRINT.md          ← this file
  server/               Spring Boot RP
  ios/                  XcodeGen iOS app
```
