# Faisal TV — Neon Plan (Implementation Spec)

This plan delivers a lightweight, native-code-free Android TV IPTV client (targeting **under 8 MB APK** and **under 120 MB RAM**) built in pure Kotlin with `androidx.leanback` and `ExoPlayer 2.19.1`, designed to run identically on 2015-era Android 5.1.1 AOSP TVs and modern Android 11 TVs. To eliminate physical-TV debug loops, every risk — API 21 compatibility, cleartext HTTP, unencoded Wowza stream URLs, D-pad focus traps, and live HLS playback — is mechanically verified on a laptop using an API 22 emulator on home Wi-Fi and Android Studio `NewApi` lint before a single Drive sideload confirmation on the real TV. Implementation is sliced into 4 phases (15 small, self-verifiable tasks for Gemini 3.8 Flash) totaling **~12–16 hours across 3–4 days**, with a playable vertical slice ready at the end of Phase 1.

---

## 1. Architecture

Single-module Android app (`:app`) with a clean, flat 4-layer structure. No Hilt/Dagger, no Room, no multi-module overhead — manual dependency wiring in a tiny `AppContainer` inside `Application`.

**Data flow:**
`FaisalApiService (Retrofit)` → `ContentRepository (in-memory cache + SharedPreferences for Favorites/Recents)` → `ViewModel (LiveData)` → `UI (Leanback Fragments / PlayerActivity)` → `ExoPlayer`

| Layer / Package | Responsibility | Key Decisions Living Here |
| --- | --- | --- |
| `data.api` | Retrofit interface, DTOs (`ChannelDto`, `MediaDto`, `CategoryDto`, `PaginatedResponse<T>`), OkHttp client | Base URL, 10s connect/read timeouts, cleartext HTTP, URL space-encoding sanitizer |
| `data.local` | `PrefsStore` wrapping `SharedPreferences` | Storing Favorite channel IDs (ordered list) and Recent channel IDs (last 10) as comma-separated strings — no SQLite/Room needed |
| `data.repository` | `ContentRepository` | Fetches & groups channels by category, merges Favorites/Recents rows, holds in-memory cache for instant back-navigation, maps network/403 errors to typed `Result` states |
| `ui.home` | `MainActivity` + `HomeFragment` (`BrowseSupportFragment`) + `CardPresenter` | 10-foot category rows, focus scaling, poster loading, launching `PlayerActivity` directly on OK |
| `ui.player` | `PlayerActivity` + custom D-pad overlay | ExoPlayer lifecycle (`onStart`/`onStop` release), HLS playback, channel zapping (Up/Down), error retry |
| `ui.common` | `ErrorFragment` / `LoadingFragment` | Friendly offline/403 screens with D-pad-focusable "Retry" button |

**Critical Manifest Decision (Wisdom-Share AOSP TVs):**
Because Wisdom-Share is a mobile-AOSP launcher (not certified Android TV), `MainActivity` must declare **both** `CATEGORY_LAUNCHER` and `CATEGORY_LEANBACK_LAUNCHER`, with `android:screenOrientation="landscape"`, `android.software.leanback` (`required="false"`), and `android.hardware.touchscreen` (`required="false"`). Without `CATEGORY_LAUNCHER`, the app icon will be invisible on the TV home screen.

---

## 2. Tech Stack

All versions below are verified for **minSdk 21**, **compileSdk 35**, **targetSdk 34** (or 35), **Kotlin 1.9.25**, **AGP 8.5.x**, and **Java 8 desugaring disabled** (not needed when avoiding `java.time`).

| Library / Tool | Exact Coordinate & Version | Why |
| --- | --- | --- |
| **Leanback UI** | `androidx.leanback:leanback:1.0.0` | Proven API 21–35 D-pad row browsing, focus memory, and 10-foot navigation out of the box (`1.2.0` is also stable, `1.0.0` has minimal transitive deps). |
| **ExoPlayer (HLS only)** | `com.google.android.exoplayer:exoplayer-core:2.19.1`, `com.google.android.exoplayer:exoplayer-hls:2.19.1`, `com.google.android.exoplayer:exoplayer-ui:2.19.1` | Last 2.x line; supports API 16+, zero native `.so` files, and splitting core+hls+ui cuts ~4 MB vs. full ExoPlayer. |
| **Retrofit + Gson** | `com.squareup.retrofit2:retrofit:2.9.0`, `com.squareup.retrofit2:converter-gson:2.9.0` | Boring, zero-reflection-bloat JSON parsing (`@SerializedName`); avoids KSP/kapt build failures for 3.8 Flash. |
| **OkHttp** | `com.squareup.okhttp3:okhttp:4.12.0` | Last 4.x release supporting API 21; rock-solid HTTP/1.1 connection pooling. |
| **Glide** | `com.github.bumptech.glide:glide:4.16.0` | API 14+; handles bitmap pooling and `RGB_565` decoding to prevent OOM on 1 GB RAM TVs. |
| **Coroutines + Lifecycle** | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`, `androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2`, `androidx.lifecycle:lifecycle-livedata-ktx:2.6.2` | Clean async API calls with automatic cancellation; `2.6.2` has zero API 23+ surprises. |
| **AppCompat / Core** | `androidx.appcompat:appcompat:1.6.1`, `androidx.core:core-ktx:1.12.0` | Stable baseline compatible with API 21 and AGP 8.5. |

**Prerequisite Gate — Android 4.4 (API 19) Fallback:**
Before Phase 1 starts, check **Settings > About** on the two unchecked TVs. If either is **Android 4.4 (API 19)**:

1. Drop `minSdk` to `19`, enable `multiDexEnabled = true`.
2. Swap OkHttp to `3.12.13` and Retrofit to `2.6.4` (last API 19 releases).
3. Replace `androidx.leanback` with a custom `RecyclerView` (vertical rows of horizontal `RecyclerView`s) using `View.OnFocusChangeListener` for scale/border focus states.

---

## 3. API Contract

**Base URL:** `http://apiislamabad.streamhome.co/api/v1/` (plain HTTP; requires `android:usesCleartextTraffic="true"` in `<application>`).

**Assumptions for uninspected endpoints:** Only `/channels` response JSON was verified in the prompt. We assume `/movies`, `/music`, and `/videos` follow the same Laravel pagination envelope (`data`, `links`, `meta`) and similar item fields (`id`, `title`, `poster`, `links`, `categories`). Phase 1 will log raw responses from `/movies` and `/music` on home Wi-Fi during emulator testing and adjust DTOs if field names differ (e.g., `url` vs `links.android`).

### Endpoints

| Endpoint | Purpose | Phase | Notes |
| --- | --- | --- | --- |
| `GET /channels?includes=categories&limit=250` | Fetch all 56 live TV channels + categories in 1 request | Phase 1 | Primary data source; group client-side by `categories[0].name`. |
| `GET /movies?includes=categories&limit=100` | Fetch VOD movies | Phase 3 | Paginate via `links.next` or `page=N` if `meta.last_page` is greater than 1. |
| `GET /music?includes=categories&limit=100` | Fetch music/albums | Phase 3 | If items represent albums, fetch tracks via `/videos?includes=categories&albumName={title}`. |
| `GET /home` | Provider featured/home feed | Optional | Skip in Phase 1; `/channels` already gives the full catalog cleanly. |
| `GET /channels/views/` | View counts | Skip | `views` is already present on each channel object; omit to save bandwidth. |

### Core Data Models & URL Sanitization

```kotlin
// Paginated envelope
data class PaginatedResponse<T>(
    val data: List<T> = emptyList(),
    val meta: MetaDto? = null
)

data class ChannelDto(
    val id: Int,
    val title: String,
    val poster: String?,
    val views: Long = 0,
    val status: Int = 0,
    val links: LinksDto?,
    val categories: List<CategoryDto> = emptyList()
)

data class LinksDto(val android: String?, val web: String?, val ios: String?)
data class CategoryDto(val id: Int, val name: String)
```

**Mandatory URL Sanitizer (Critical):**
Stream URLs contain literal spaces (e.g., `http://10.15.237.10:1935/live/GEO SUPER.stream/playlist.m3u8`). VLC auto-encodes spaces; ExoPlayer's HTTP stack can reject them. Every stream and poster URL must pass through:

```kotlin
fun sanitizeUrl(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.replace(" ", "%20")
```

Stream URL selection priority: `links?.android ?: links?.web ?: links?.ios`.

### Error Handling Matrix

| Condition | Detection | User-Facing Message & Action |
| --- | --- | --- |
| **Off Home Network (ISP IP block)** | HTTP `403 Forbidden` | *"Not connected to Faisal home network. Connect this TV to home Wi-Fi and press Retry."* + `[Retry]` |
| **No Internet / Server Down** | `IOException` / `UnknownHostException` / `SocketTimeoutException` | *"Can't reach the TV server. Check your Wi-Fi connection and press Retry."* + `[Retry]` |
| **Empty / Malformed Response** | HTTP 200 with empty `data` or `JsonSyntaxException` | *"No channels available right now."* + `[Retry]` |
| **Broken Individual Stream** | ExoPlayer `PlaybackException` | Overlay inside player: *"This channel is currently offline."* + `[Retry]` `[Next Channel]` `[Back]` |

---

## 4. Screens & Navigation

Live TV should be **zero-friction**: browse → press OK → watch. **Do NOT add a channel detail screen for Live TV** — it wastes an extra remote click. (Movies in Phase 3 can also direct-play on OK, with title/category shown in the focused header.)

### Screen 1: Splash / Loading (`HomeFragment` initial state)

* **Layout:** Dark charcoal background (`#0E1117`), centered app wordmark ("Faisal TV"), subtle progress spinner, status text ("Loading channels...").
* **D-pad Flow:** None while loading; transitions automatically to Home rows when `/channels` returns (typically under 1 second on LAN).
* **Back:** Exits app cleanly.
* **On Error:** Replaces spinner with Error view containing a focused `[Retry]` button (OK re-fetches; Back exits).

### Screen 2: Home (`BrowseSupportFragment` — Category Rows)

* **Layout:** Left sidebar (Leanback Headers rail, collapsible on DPAD_RIGHT) listing:
  1. **★ Favorites** (hidden if empty)
  2. **Recently Watched** (hidden if empty, max 10)
  3. **All Channels** (sorted by `views` descending — puts Geo Super, Hum TV, PTV Sports near the front)
  4. **Dynamic Category Rows** from API (e.g., *Sports*, *News*, *Entertainment*, *Dramas*, etc.)
  5. **Movies** / **Music** rows (added in Phase 3)
* **D-pad Flow:**
  * `UP/DOWN`: Move between category rows (or sidebar categories when sidebar is focused).
  * `LEFT/RIGHT`: Scroll horizontally through channel poster cards; pressing `LEFT` on the first card opens the category sidebar.
  * `OK` (short press): Immediately launches `PlayerActivity` for the focused channel and records it in *Recently Watched*.
  * `OK` (long press, 600ms) or `MENU` key: Toggles **Favorite** (shows brief toast + updates ★ badge on card and Favorites row).
  * `Back`: If in content rows, jumps focus to the sidebar (standard Leanback behavior); if in sidebar, shows a 2-second *"Press Back again to exit"* toast (prevents accidental exits).

### Screen 3: Full-Screen Player (`PlayerActivity`)

* **Layout:** Edge-to-edge `SurfaceView`-backed `PlayerView` (`keepScreenOn="true"`). No persistent chrome.
* **Auto-hiding OSD Overlay (appears for 4 seconds on any key press, then fades out):**
  * **Bottom bar:** Channel poster thumbnail, Channel Title (28sp bold), Category pill, *"LIVE"* red badge, subtle remote hint (`▲▼ Switch Channel  •  ★ Menu to Favorite`).
  * **Center:** Spinner only while `STATE_BUFFERING`.
* **D-pad Flow:**
  * `UP / CHANNEL_UP`: Zap to next channel in the active row list (wraps around).
  * `DOWN / CHANNEL_DOWN`: Zap to previous channel in the active row list.
  * `OK / CENTER`: Toggle OSD overlay visibility (for VOD movies in Phase 3: play/pause + show seek bar).
  * `LEFT / RIGHT`: Ignored on Live TV; seeks `-15s / +15s` on VOD movies (Phase 3).
  * `MENU` or `LONG_PRESS OK`: Toggle Favorite for current channel.
  * `Back`: If OSD is visible, hides OSD; if hidden (or second Back), finishes `PlayerActivity` cleanly and returns to the exact focused card on Home.

---

## 5. Beautiful-Interface Spec (10-Foot TV UI)

Designed for **10-foot viewing on 720p/1080p TVs** with zero GPU-heavy blur/alpha effects that lag on dual-core ARM chips.

### Color Palette

| Token | Hex | Usage |
| --- | --- | --- |
| `bg_canvas` | `#0E1117` | App background (deep ink, richer than pure black, easy on eyes in dark rooms) |
| `bg_sidebar` | `#161B22` | Leanback left category rail |
| `bg_card` | `#1F2633` | Card info footer & poster placeholder |
| `accent_focus` | `#38BDF8` | Crisp sky-blue focus ring & active category indicator (high contrast on dark) |
| `accent_live` | `#EF4444` | "LIVE" dot/badge & Favorite heart/star highlight (`#FBBF24` for star) |
| `text_primary` | `#F8FAFC` | Channel titles, active row headers |
| `text_secondary` | `#94A3B8` | Category subtitles, hints, unfocused rail labels |

### Typography Scale (Roboto — system font, zero APK bloat)

* **Brand Title (Sidebar top):** `26sp`, Bold, `#F8FAFC`
* **Row Category Header:** `22sp`, SemiBold, `#F8FAFC`
* **Card Title (under poster):** `16sp`, Medium, `#F8FAFC`, `maxLines="1"`, `ellipsize="end"`
* **Player OSD Channel Title:** `28sp`, Bold, `#F8FAFC`
* **Error / Empty State Heading:** `24sp`, SemiBold; body `18sp`, `#94A3B8`

### Poster Cards & Overscan Safe Area

* **TV Overscan Margins:** `48dp` horizontal, `27dp` vertical (Leanback default — prevents edges clipping on older Chinese TV panels).
* **Live TV Channel Card Size:** `220dp × 124dp` image area (**16:9 landscape**) + `44dp` dark footer strip for title/category = `220dp × 168dp` total. `scaleType="centerCrop"` (or `fitCenter` with dark padding if provider posters are square channel logos — implementer should inspect in Phase 1 emulator and switch to `fitCenter` with `12dp` padding if logos get cropped).
* **Movie Card Size (Phase 3):** `160dp × 240dp` (**2:3 portrait**).
* **Card Corner Radius:** `8dp` via `CardView` (`cardElevation="4dp"`).

### Focus States & Motion (Lightweight for Dual-Core CPU)

* **Focused Card:** Scales to **1.08×** over `120ms` (`DecelerateInterpolator`), `3dp` solid `#38BDF8` stroke border, `cardElevation` bumps to `12dp`, title switches from `#CBD5E1` to `#F8FAFC`.
* **Unfocused Card:** Scale `1.0×`, `0dp` border, subtle dim overlay (or `0.85f` alpha on text only — avoid alpha on Bitmaps for GPU perf).
* **No background backdrop cross-fading** (Leanback's `BackgroundManager` full-screen bitmap swaps cause GC stutters and OOM on 1 GB RAM TVs — keep the background static `#0E1117`).

---

## 6. Phased Task List (for Gemini 3.8 Flash in Anti-Gravity)

Each task is scoped to 1–3 files with a concrete verification step 3.8 Flash can run immediately.

### Phase 0: Pre-Flight (Human — 15 min)

* **T0.1 — Check the other two TVs:** Open *Settings > About* on both unchecked TVs. Confirm Android version is 5.0+ (API 21+). *(If 4.4, activate the API 19 fallback in Section 2 before T1.1.)*
* **T0.2 — Emulator & Repo Setup:** Create GitHub repo; create an Android Studio AVD: **Android TV (720p) or Tablet/TV 1280×720**, **Android 5.1 (API 22)**, **Google APIs OFF (AOSP)**.

---

### Phase 1: Minimal Working App — Browse & Play One Channel (Vertical Slice)

*Goal: Launch on API 22 emulator, load all 56 live channels from the real API, press OK on Geo Super, and watch it play smoothly.*

* **T1.1 — Project Scaffold & Manifest**
  * *Deliverable:* New Kotlin Android project (`minSdk 21`, `targetSdk 34`, `compileSdk 35`), exact dependencies from Section 2, `usesCleartextTraffic="true"`, `INTERNET` + `ACCESS_NETWORK_STATE` permissions, `MainActivity` with both `LAUNCHER` and `LEANBACK_LAUNCHER`, landscape lock, `lint { abortOnError = true; checkOnly += "NewApi" }`.
  * *Accept & Verify:* `./gradlew assembleDebug lintDebug` passes with 0 errors and 0 `NewApi` warnings; blank dark screen launches on API 22 emulator.
* **T1.2 — API Layer & URL Sanitizer**
  * *Deliverable:* `FaisalApiService`, DTOs (`PaginatedResponse`, `ChannelDto`, `LinksDto`, `CategoryDto`), `NetworkModule` (Retrofit + OkHttp with 10s timeouts), `UrlSanitizer` (`replace(" ", "%20")`), unit test for URL sanitizer & JSON parsing using the real sample channel JSON.
  * *Accept & Verify:* `./gradlew testDebugUnitTest` passes.
* **T1.3 — Repository & Error Classification**
  * *Deliverable:* `ContentRepository` fetching `GET /channels?includes=categories&limit=250`, grouping channels by category + "All Channels" (sorted by `views` desc), returning `Sealed Result` (`Success`, `Forbidden403`, `NetworkError`).
  * *Accept & Verify:* Unit test verifies grouping, sorting, and 403 vs. IOException mapping.
* **T1.4 — Basic Leanback Home Screen**
  * *Deliverable:* `HomeFragment` (`BrowseSupportFragment`) + `ChannelCardPresenter` (using Glide with `RGB_565` and `override(320, 180)`) + loading state + basic error screen with focusable `[Retry]` button.
  * *Accept & Verify:* Launch on API 22 emulator on home Wi-Fi → all 56 channels appear in category rows; arrow keys navigate smoothly; turning off Wi-Fi shows friendly error screen and `[Retry]` recovers when Wi-Fi returns.
* **T1.5 — Full-Screen ExoPlayer Activity**
  * *Deliverable:* `PlayerActivity` using `exoplayer-core` + `exoplayer-hls` + `exoplayer-ui`, `SurfaceView`, `keepScreenOn="true"`, conservative `DefaultLoadControl` (min 15s / max 30s / playback 2.5s), full player release in `onStop()`. Wire `HomeFragment` card click → `PlayerActivity` passing sanitized stream URL + title.
  * *Accept & Verify:* On API 22 emulator (home Wi-Fi), click **Geo Super**, **Hum TV HD**, and **Geo Entertainment HD** → video + audio play within 3–5 seconds; press `Esc` (Back) → returns cleanly to Home with audio stopped and decoder released.

---

### Phase 2: Remote-Control Polish, Channel Zapping & Favorites

*Goal: Make it feel like a real family TV app — fast channel switching, favorites, recents, and resilient playback.*

* **T2.1 — Player OSD & Channel Zapping**
  * *Deliverable:* Pass current category's channel list + index to `PlayerActivity`. Implement `KEYCODE_DPAD_UP` / `KEYCODE_CHANNEL_UP` (next channel) and `DOWN` (prev channel), plus the 4-second auto-hiding bottom OSD bar (poster, title, LIVE badge, remote hints).
  * *Accept & Verify:* Zap through 10 channels in a row on emulator using Up/Down arrow keys; no black screen, no memory leak, OSD auto-hides cleanly.
* **T2.2 — Stream Error Recovery Overlay**
  * *Deliverable:* Handle ExoPlayer `onPlayerError` inside `PlayerActivity`: show in-player dark card (*"This channel is currently offline"*) with 3 D-pad focusable buttons: `[Retry]`, `[Next Channel]`, `[Back to Home]`.
  * *Accept & Verify:* Test with a deliberately bad URL in a debug override or dead channel → overlay appears, focus lands on `[Retry]`, `[Next Channel]` immediately plays the next working channel.
* **T2.3 — Favorites & Recently Watched (`PrefsStore`)**
  * *Deliverable:* `PrefsStore` (`SharedPreferences`) storing favorite IDs and last 10 recent IDs. Add **★ Favorites** and **Recently Watched** rows to top of `HomeFragment`. Wire long-press `OK` (on Home card) and `MENU` key (in Home or Player) to toggle favorite with a toast + ★ badge on card.
  * *Accept & Verify:* Favorite 2 channels, play 3 channels, force-stop and relaunch app on emulator → Favorites and Recents rows persist accurately.
* **T2.4 — Back-Button Polish**
  * *Deliverable:* Double-Back-to-exit guard on Home sidebar (*"Press Back again to exit"*); single Back in Player hides OSD if visible, otherwise exits Player.
  * *Accept & Verify:* Verified via `Esc` key on emulator from every screen state.

---

### Phase 3: Movies, Music & Visual Polish

*Goal: Complete the provider's catalog and apply the full 10-foot visual spec.*

* **T3.1 — Inspect & Integrate Movies & Music Endpoints**
  * *Deliverable:* Fetch `/movies?includes=categories` and `/music?includes=categories` (log raw JSON once on emulator to confirm exact schema), add DTOs and rows to `HomeFragment`, add seek (`LEFT`/`RIGHT` ±15s) and Play/Pause (`OK`) when playing VOD items in `PlayerActivity`.
  * *Accept & Verify:* Browse and play at least 1 movie and 1 music video on API 22 emulator; seek forward/back works with arrow keys.
* **T3.2 — Apply Full Visual Spec**
  * *Deliverable:* Apply exact color palette (`#0E1117`, `#161B22`, `#38BDF8` focus ring), `1.08×` focus scale animation (`120ms`), `8dp` rounded cards, custom app icon + Leanback banner (`320×180`), and `fitCenter` vs `centerCrop` tuning based on real poster aspect ratios.
  * *Accept & Verify:* Visual inspection on 720p TV emulator: every focused element is unmistakable from 10 feet away; zero clipped text or overscan cutoff.
* **T3.3 — Memory & Release Build Hardening**
  * *Deliverable:* Configure Glide `AppGlideModule` (cap memory cache at 16 MB, disk cache at 50 MB, `RGB_565`), enable R8/ProGuard (`minifyEnabled = true`, `shrinkResources = true`) with Keep rules for Retrofit/Gson DTOs, generate signed release APK.
  * *Accept & Verify:* Release APK is **under 10 MB**, installs cleanly on API 22 emulator, and survives 15 minutes of playback + zapping with RAM under 120 MB in Android Studio Profiler.

---

### Phase 4: Final Validation & Single-TV Sideload

* **T4.1 — Full Laptop Validation Suite** (execute Section 7 laptop checklist).
* **T4.2 — Upload APK to Google Drive & Single-TV Confirmation** (execute Section 7 TV checklist; then install same APK on remaining 4 TVs).

---

## 7. Validation Plan (Build Once, Confirm Once)

### A. Laptop Validation Checklist (ALL must pass before touching a TV)

1. **Static API 21 Proof:** Run `./gradlew lintRelease`. Confirm **0 `NewApi` errors** and **0 `InlinedApi` issues**.
2. **Zero Native Code Proof:** Inspect release APK via *Android Studio > Build > Analyze APK*. Confirm there is **no `lib/` folder** (zero `.so` files), **zero Play Services classes**, and APK size is **under 20 MB** (target ~5–8 MB).
3. **API 22 Cold Start & Install:** Install **release** APK (`adb install -r app-release.apk`) on the **Android 5.1 (API 22) AOSP emulator** (no Google Play). Verify cold start to channel grid takes **under 4 seconds**.
4. **Live Stream Playback (on Home Wi-Fi):** Play **Geo Super**, **Hum TV HD**, **Geo Entertainment HD**, plus 3 random channels from other categories. Confirm video + audio stay in sync for 2+ minutes.
5. **100% Remote-Only Navigation Audit:** Unplug/ignore mouse. Using **only** keyboard arrows (`↑↓←→`), `Enter` (OK), `Esc` (Back), and `Menu`:
   * Navigate every row and sidebar category.
   * Favorite/unfavorite a channel.
   * Enter player, zap 5 channels up/down, return to Home, verify focus returns to the last card.
   * Confirm **zero** focus dead-ends (focus never disappears off-screen).
6. **Offline / 403 Resilience:** Toggle emulator Wi-Fi OFF (or connect laptop to mobile hotspot to trigger real ISP 403). Launch app → verify friendly message appears without crash → reconnect home Wi-Fi → press `[Retry]` → channels load.
7. **Memory & Leak Check:** Watch Android Studio Memory Profiler during 10 channel switches. Confirm heap stays **under 120 MB** and drops cleanly after closing `PlayerActivity`.
8. **Android 11 Smoke Test (Optional 5-min check):** Install same release APK on an API 30 emulator to confirm it also works on the two newer TVs (including cleartext HTTP on Android 9+).

### B. Single-TV Confirmation Checklist (One Drive Sideload)

1. Download `FaisalTV-v1.0.apk` from Google Drive in the TV browser and install.
2. Confirm app icon appears in Wisdom-Share "Smart cloud TV" launcher (verifies `CATEGORY_LAUNCHER`).
3. Launch with IR remote → channels load → poster art renders cleanly without lag.
4. Press OK on Geo Super → plays smoothly full-screen with audio.
5. Press Up/Down to zap channels, Back to return to Home, Back twice to exit.
6. **If all 5 pass:** Sideload the exact same APK onto the remaining 2 old TVs and 2 Android 11 TVs.

---

## 8. Risks & Mitigations

| # | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| **1** | **Unchecked TVs are Android 4.4 (API 19)** | `minSdk 21` APK refuses to install ("Parse error") on those TVs. | **Phase 0 gate:** Check Settings > About on both TVs before writing a line of code. If 4.4, immediately apply the documented API 19 fallback (OkHttp 3.12.13 + custom RecyclerView grid). |
| **2** | **Unencoded spaces in Wowza stream URLs** (`GEO SUPER.stream`) | ExoPlayer fails with `400 Bad Request` or `InvalidResponseCodeException` even though VLC plays it. | Mandatory `UrlSanitizer` (`trim().replace(" ", "%20")`) applied to every stream and poster URL in `data.api`, verified by unit test and live emulator playback. |
| **3** | **Hardware video decoder exhaustion / OOM on 1 GB RAM dual-core TV** | Black screen on second channel, stuttering, or app killed mid-match. | Release ExoPlayer strictly in `onStop()` (never `onDestroy()`), use `SurfaceView` (not `TextureView`), cap ExoPlayer buffers (15–30s), cap Glide cache (16 MB RAM) with `RGB_565`, and disable Leanback full-screen backdrop transitions. |
| **4** | **App icon missing on Wisdom-Share launcher** | APK installs fine, but family cannot find or open the app without going into Settings > Apps. | Declare **both** `CATEGORY_LAUNCHER` and `CATEGORY_LEANBACK_LAUNCHER` on `MainActivity`, plus `leanback required="false"`. |
| **5** | **R8/ProGuard strips Retrofit/Gson DTO fields in release build** | Debug build works on emulator, but release APK shows empty channel list or crashes parsing JSON. | Add explicit `@Keep` annotations on all DTO classes + Gson ProGuard rules in `proguard-rules.pro`, and run the **entire** laptop validation suite against the **release** APK, never debug. |

---

## 9. Time Estimate

| Phase | Scope | Est. Time (3.8 Flash + Imran Review) | Target Window |
| --- | --- | --- | --- |
| **Phase 0** | Check 2 TVs + setup GitHub repo & API 22 AVD | 30 min | Day 1 (Morning) |
| **Phase 1** | Scaffold, API, Repository, Leanback Home, ExoPlayer (playable MVP) | 4–5 hours | Day 1 |
| **Phase 2** | Channel zapping, OSD, stream error recovery, Favorites & Recents | 3–4 hours | Day 2 |
| **Phase 3** | Movies/Music integration, 10-foot visual polish, R8 release build | 3–4 hours | Day 3 |
| **Phase 4** | Full laptop validation + single-TV Drive sideload + rollout to all 5 TVs | 1.5–2 hours | Day 3–4 |
| **Total** | **End-to-end production-ready v1.0** | **~12–16 hours** | **3–4 days** |
