# Nelitv (by Neliplay)

**Nelitv** is an Android live television, Azam TV sports, Swahili cinema, series, short drama, and offline VOD streaming application built with **Kotlin**, **Jetpack Compose (Material Design 3)**, **AndroidX Media3 (ExoPlayer)**, and **Room**.

---

## Key Features

### 1. Live TV & Azam TV Sports Streaming
- **Live Channels**: Watch Azam Sports HD (1, 2, 3, 4), Sinema Zetu, UTV, TBC 1, Clouds TV, Wasafi TV, ITV, East Africa TV, Star TV, ZBC, Channel Ten, and international sports, news, movies, kids, and religious channels.
- **Anti-Stutter & Auto-Recovery Engine**: Built on AndroidX Media3 (`LivePlayerController`) with automatic stall/freeze detection (`checkAndAutoFixLiveStreamStall`), low-latency buffer tuning, and seamless fallback across multiple stream URLs.
- **Kiswahili & English Audio Track Switcher**: Prioritizes Kiswahili commentary and audio tracks on Azam TV channels while preserving Kiswahili-only programming (`Sinema Zetu`, `UTV`, `Azam Two`).
- **Adaptive Quality & Mobile Data Saver**: Supports `Auto Adaptive`, `Low Bando Saver`, `360p Saver`, and `Full HD` streaming over 3G/4G/5G and Wi-Fi.

### 2. Movies, Series, Short Dramas & Cartoons (VOD)
- **Swahili Translated Movies ("Movies Zilizotafsiriwa") & Bongo Flava / Cinema**: Curated catalog with TMDB cast & poster integration and guaranteed image fallback.
- **Offline Downloads**: Download movies, series episodes, and short dramas to local storage for offline playback (`file://` URI resolution with Room persistence).
- **Watchlist & Playback History**: Local Room database persistence for watchlists, favorites, and resume positions.

### 3. HarakaPay TZS Mobile Money Subscriptions (VIP & Premium)
- **USSD Push Payments (`PaymentService`)**: Direct integration with HarakaPay (`POST /api/v1/collect` and `GET /api/v1/status/{orderId}`) supporting **M-Pesa**, **Tigo Pesa / Mixx by Yas**, **Airtel Money**, and **HaloPesa**.
- **Subscription Packages**:
  - **Kwa Siku Mbili (48 Hours / 2 Days)**: `TZS 1,000`
  - **Kwa Wiki (7 Days / 1 Week)**: `TZS 3,500`
  - **Kwa Mwezi (30 Days / 1 Month)**: `TZS 15,000`
- **Background Device IP & Cross-Device Account Sync**:
  - Automatically links verified subscriptions to the device in the background (even before login/signup) and persists subscription records in Room (`DeviceSubscriptionEntity`).
  - Post-payment **Login / Sign Up** card allows users to bind their active subscription to their account for cross-device access and Smart TV casting.
- **Real-Time Subscription Status & Channel Access**:
  - Users with an active, verified subscription retain uninterrupted access to all channels—even if free channels are later locked—until their subscription period expires.
  - Expired or unpaid accounts are prompted to subscribe when selecting locked channels, and channels unlock immediately upon payment verification.

### 4. Smart TV Cast (HD Anti-Stutter)
- **Real Device Discovery (`NeliCastManager`)**: Discovers nearby Smart TVs, Android TV boxes, and wireless display receivers via Android `MediaRouter` and Wi-Fi multicast/network scanning.
- **High-Quality Anti-Stutter Stream Engine**: Supports `1080p Full HD • 60fps Anti-Stutter`, `720p HD Smooth`, and `Auto HD Anti-Stutter` with one-tap buffer boost and user/VIP status synchronization.

### 5. Mini Admin Panel (Admin-Only)
- Accessible from the **Account** tab when signed in with the administrator credentials (`Admin@login.com`).
- **Live Channel Management**: Add new live TV channels, edit stream URLs, or lock/unlock individual or all channels in real time.
- **VOD Catalog Management**: Publish new Movies, Series, Short Dramas, or Cartoons directly to the app.
- **Admin Broadcast Banner & Push Notifications**: Send real-time announcements positioned either above or below the Home hero slider.

### 6. Responsive Layout & Theme Support
- **Adaptive Screen Profiles (`NeliResponsiveLayout`)**: Automatically scales grids, poster cards, hero banners, and typography across compact phones (Itel, Tecno), standard/large phones (Samsung, Infinix), foldables, and tablets.
- **Appearance Modes (`NeliThemeManager`)**: Instant switching between **Black Theme (Dark Mode)** and **White Theme (Light Mode)**.
- **Home Screen Widget & APK Sharing**: Includes an Android AppWidget (`NeliTvAppWidgetProvider`), QR code generator for sharing the APK download link, and GitHub Release update checker (`NeliUpdateManager`).

---

## Tech Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material Design 3
- **Media Playback**: AndroidX Media3 (`ExoPlayer`, `MediaSession`, HLS & Progressive MP4/TS support)
- **Local Persistence**: Room Database (`NeliDatabase`, `NeliMediaDao`) + `SharedPreferences` state synchronization
- **Networking & APIs**:
  - HarakaPay REST API (`PaymentService` / `HarakaPayRepository`)
  - TMDB v3 API (`TmdbApiClient`)
  - Firebase Auth REST & Local Room Account Sync (`FirebaseAuthRepository`)
- **Testing**: JUnit 4 + Robolectric (`ExampleRobolectricTest`)

---

## Configuration & Secrets

API keys are injected into `BuildConfig` via the Secrets Gradle Plugin (`.env` / `.env.example`). Configure the following keys in the **Secrets** panel:

```env
HARAKAPAY_API_KEY=YOUR_HARAKAPAY_API_KEY
TMDB_API_KEY=YOUR_TMDB_API_KEY
FIREBASE_API_KEY=YOUR_FIREBASE_API_KEY
```

---

## Building & Running Tests

- **Compile Debug APK**:
  ```bash
  gradle assembleDebug
  ```
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## Support & Contact

- **Brand**: Neliplay / Nelitv
- **Customer Care (WhatsApp & Call)**: `+255 760 816 851` (Neliplay Customercare • Alex Michael Baineth)
