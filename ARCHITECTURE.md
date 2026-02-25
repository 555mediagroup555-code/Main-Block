# Monorepo Architecture (Current State)

## System overview
This repository currently contains:

- **Android client** in `SignalsApp/SignalsApp-main` (Kotlin + Jetpack Compose).
- **Backend module directory** in `backend` (currently empty; no FastAPI/Node source files yet).

Until the folder layout is normalized to `/android-app` and `/backend`, treat this as one integrated mobile + API system where the app consumes backend endpoints and must stay in sync with API schema changes.

## Android module architecture

### Build and tooling
- Gradle Kotlin DSL project with one app module (`:app`).
- Android settings and dependency versions managed by `settings.gradle.kts` and `gradle/libs.versions.toml`.
- Compose UI is enabled via `buildFeatures { compose = true }`.
- Networking stack uses Retrofit + Gson + OkHttp (with logging interceptor).

### Runtime structure
- `MainActivity` hosts a single Compose screen (`SignalsScreen`).
- UI state is held in Compose `remember` state variables (base URL, list data, loading, filters/sorts).
- Data loading flow:
  1. Build Retrofit API client from entered base URL.
  2. Call `GET /scan-lite` asynchronously.
  3. Store response in-memory and render list cards.
- Display pipeline applies:
  - timeframe filtering,
  - recommendation-priority sorting,
  - coin sorting by symbol / sell volume / market cap.

### API contract currently expected by Android
`GET /scan-lite` returns `List<LiteSignal>` with fields:
- `symbol`, `signal`, `score`, `timeframe`, `price`, `summary`
- `entry_zone` (array of numbers)
- `stop_loss` (number)
- `take_profit` (array of numbers)
- `daily_sell_volume` (mapped to `dailySellVolume`)
- `market_cap` (mapped to `marketCap`)

## Backend module architecture
- The `backend` directory exists but currently has no implementation files.
- There is no runnable FastAPI/Node service committed yet.
- Effective current contract source-of-truth is the Android `LiteSignal` model and `SignalsApi.scanLite()` declaration.

## Integration rules going forward
When backend API changes are introduced:
1. Update backend endpoint implementation and response schema.
2. Update Android data model(s) in `Api.kt` to match field names/types.
3. Update Android UI rendering/sorting/filtering logic if semantics changed.
4. Rebuild both modules before merging.

Recommended next step: add a backend OpenAPI spec and generate/validate Android DTOs from that spec to reduce drift.
