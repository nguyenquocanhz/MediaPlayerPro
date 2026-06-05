# Changelog - Premium Media Player

All notable changes to this project will be documented in this file.

## [1.1.0] - 2026-06-05

### Added
* **Recently Watched Videos (Lịch sử xem)**:
  * Persistent storage for the last 30 played videos (both local files and online streams) using JSON serialization in `SharedPreferences`.
  * New **GẦN ĐÂY** sub-tab in the Video navigation drawer showcasing play history, play timestamp, and a "Clear History" button.
  * Click actions on any media (grid/list local videos, HLS streams, online movies, and adult tabs) automatically record to history.
* **Adult Search & Stream Integration (XXNAPI & SpankBang)**:
  * Integrated **XXNAPI** (XNXX) and **SpankBang** bottom navigation tabs.
  * Custom scraping engine ([AdultScrapers.kt](file:///f:/MediaPlayer/app/src/main/java/com/example/mediaplayer/data/AdultScrapers.kt)) utilizing regex patterns to extract HLS master playlists (`.m3u8`) and high-quality MP4 sources.
  * Search engine with video thumbnail previews (via Coil `AsyncImage`), durations, source labels, and an elegant progress overlay resolving streaming URLs.
  * Graceful fallback to public sample streams if internet connectivity or DNS/ISP restrictions block scraper endpoints.
* **Visual Rebranding & Identity**:
  * Clean, vector-based adaptive launcher icons ([ic_launcher_foreground.xml](file:///f:/MediaPlayer/app/src/main/res/drawable/ic_launcher_foreground.xml) and [ic_launcher_background.xml](file:///f:/MediaPlayer/app/src/main/res/drawable/ic_launcher_background.xml)) designed with a modern blue theme, track arc, and seek dot.
  * Density-specific legacy mipmap WebP icons generated across all sizes (`mdpi` 48x48 up to `xxxhdpi` 192x192).
  * Brand-new `SplashView` in [MainActivity.kt](file:///f:/MediaPlayer/app/src/main/java/com/example/mediaplayer/MainActivity.kt) featuring a premium blue vertical gradient, floating background music notes, audio visualizer bar layouts, breathing scaling animation, and loading indicators.

### Improved
* **Pull-To-Refresh Gesture**:
  * Unified and overhauled reloading mechanisms across all search/scan interfaces.
  * Replaced custom loading UI with standard Compose Material 3 `PullToRefreshBox` in Videos list, Music scan, NCT search, and adult search grids.
* **Brace Matching & Compilation Integrity**:
  * Fixed a syntax issue at line 181 of [MainScreen.kt](file:///f:/MediaPlayer/app/src/main/java/com/example/mediaplayer/ui/main/MainScreen.kt) where commented braces caused syntax tree displacement.
  * Resolved drawing math parameter errors inside splash screen Canvas operations.
  * Ensured 100% clean Gradle builds.

### Removed
* **DJ Mixer**:
  * Completely removed DJ Mixer code, including manager, UI layouts, sub-tabs, and deleted the corresponding files `DjMixerManager.kt` and `DjMixerScreen.kt`.
* **YouTube Web Player**:
  * Removed YouTube WebView feature and deleted the helper file `BackgroundPlayWebView.kt`.

---

[1.0.0] - Initial release of the application.
