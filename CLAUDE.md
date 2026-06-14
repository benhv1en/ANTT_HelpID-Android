# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository structure

Three independent components share this repo:

- `app/` — Android app (Kotlin, Jetpack Compose, Room, Firebase, WorkManager)
- `backend/` — ASP.NET Core Web API (`HelpId.Api/`) + tests (`HelpId.Api.Tests/`)
- `helper-id/` — React 19 + Vite 6 marketing site + Vercel serverless API

Operating rules and harness docs live in `harness-engineering/`. Read those before making large changes. Key doc: `harness-engineering/ke-hoach.md` (current plan) — always re-read before coding if a plan exists.

## Build and test commands

### Android

The host JDK must be 17 or 21. JDK 25 breaks `JavaVersion.parse()` in the Kotlin/Gradle version used. Override with `JAVA_HOME`:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:lintDebug
# Instrumentation tests require a running emulator or device:
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:connectedDebugAndroidTest
```

Android builds also require `app/google-services.json` (gitignored, not committed).

### Backend

```bash
cd backend/HelpId.Api && dotnet build
cd backend/HelpId.Api.Tests && dotnet test
```

### Web

```bash
cd helper-id && npm install   # first time only
cd helper-id && npm run build
cd helper-id && npx tsc --noEmit   # type check without building
```

## Architecture

### Android auth state

Two parallel auth systems exist:

- **Firebase anonymous auth** (legacy) — used for Firestore sync and the public emergency link flow via `helper-id/`
- **Backend JWT auth** (new) — `HelpIdApiAuthRepository` calls the ASP.NET Core API; access tokens (15 min) stored in `AuthTokenStore` (EncryptedSharedPreferences); refresh tokens (30-day) rotated on each use

`AuthState` sealed class in `MainActivity.kt` drives all navigation:
- `Initializing` — startup, checking token store
- `Authenticated(userId)` — valid session (either Firebase or backend JWT)
- `LocalCacheOnly(userId, isOffline)` — no valid session but Room cache exists; emergency screen still accessible; `isOffline=true` = network failure, `isOffline=false` = token rejected
- `Unauthenticated` — no session and no cache

On startup: check `AuthTokenStore` → if access token expired, call `HelpIdApiAuthRepository.refresh()` → on `NetworkError` go `LocalCacheOnly(isOffline=true)`, on `ApiError` 400-403 clear tokens and go `LocalCacheOnly` or `Unauthenticated`, on 5xx fall through to Firebase fallback.

Logout: best-effort server-side revoke → `clearTokens()` → do NOT delete Room data → check Room cache → `LocalCacheOnly` or `Unauthenticated`.

### Navigation

Navigation is a **manual state machine** — `currentScreen: MutableState<String>` in `AppNavigation` (`MainActivity.kt`). Navigation Compose is not used. All screen routing is a `when(currentScreen.value)` block.

### Data flow (offline-first)

Room (SQLite local) is the source of truth for reads. Pattern:

1. Read from Room immediately (fast, works offline)
2. Fetch from remote (Firebase Firestore or backend API)
3. Write remote result to Room
4. Pending sync flag in `SecurePrefs` if remote write fails offline

Room field encryption: sensitive fields (name, address, allergies, medical notes, emergency contacts) are encrypted with AES-GCM via AndroidKeyStore alias `helpid_sensitive_aes` before storing. Encrypted values carry prefix `enc::`.

### Backend API ownership

The backend reads `userId` from the JWT `sub` claim — never from the request body or query string. `GET /api/v1/profile` and `PUT /api/v1/profile` always operate on the caller's own profile. Frontend must not send or trust arbitrary userId.

### Room schema changes

Always bump `AppDatabase.DATABASE_VERSION`, add an explicit `Migration` object, and never use `fallbackToDestructiveMigration` for user data.

## Mandatory rules

**CHANGELOG.md** — prepend a new entry at the top after every code/doc/config change. Format:
```
dd/mm/yyyy hh:mm:ss
- what changed
```

**String resources** — all UI text via `stringResource`. Every new key must be added to all 6 locale files: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-hi/`, `values-vi/`.

**No secret logging** — never log PII, health data, phone numbers, location, Firebase tokens, JWT tokens, refresh tokens, or passwords. No health data in test fixtures.

**No binary edits** — do not edit `*.png`, `gradle-wrapper.jar`, or `helper-id/package-lock.json` unless explicitly required.

**UML use-case** — when any use-case changes (actors, flows, routes, API, SOS, QR/NFC, public profile, permissions), update and re-render 3 separate diagrams: `harness-engineering/uml-use-case-android.puml/png`, `-website.puml/png`, `-api.puml/png`. Delete the old PNG before re-rendering.

**Test logging** — every test run (unit, build/lint assertion, or manual test) must be recorded in `passed-testcases.md` or `failed-testcases.md` with: timestamp, purpose, how tested, expected vs actual result. If a previously failing test now passes, remove the fail entry.

**Vercel secrets** — keep secrets in serverless API only. Never put secrets in Vite bundle or `VITE_*` env vars.
