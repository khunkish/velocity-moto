# Verification — 2026-10-30

## Moto 4.0

- Build and Android lint pass with no errors. Debug APK remains intended for direct installation testing, min Android 8.0 / target SDK 34.
- Standalone DSP/drivetrain suite: **108 checks passed**. All 8 engines × 12 exhaust profiles produce different nonzero PCM with bounded amplitude. Tests cover gear hysteresis under GPS jitter, automatic shifting to sixth, momentary cut, disabling automatic gears, continuous RPM when rev drop is disabled, ignition cooldown, ignition off, and digital silence when muted.
- Android GPS/export regression suite: **39 checks passed** using `MotoVerification`, including valid/stale/inaccurate GPS, geodesic distance, paused time, laps, GPX XML and document writing, CSV units, and native instrument rendering.
- Android UI/audio suite: **38 checks passed** using `V4Verification`. Includes left/right and reverse wrap across all 15 dials, slow swipes, vertical drag not opening menus, gesture off, same View retained while switching, tap after swipe, category Back behavior, persisted white-gloss selection, 16 renders of four sport models × four soft themes, and AudioTrack virtual telemetry/start/stop.
- Visually inspected real Android UI at 720 × 1280 and native wide renders at 1600 × 900. White-gloss home, organized control panel, audio panel, horizontal gesture result, and light/dark sport instrument variants inspected. The light divider was softened after review.
- Fixed stale cached model titles, menus firing after directional drags, repeated queued dial notifications, long fixed-height switch descriptions, white-mode alert dialog styling, and the empty instrument space after hiding RPM/gear. Model changes keep the same cockpit View and animation clock.
- Signal generation runs on the audio thread; static gloss gradients and instrument artwork are cached. High refresh rate rendering is requested, but real phone FPS is not guaranteed. The earlier version's emulator performance observation is not a new 4.0 device performance claim.
- Credits and all three author links remain in System → Credits. No external profiles were opened or messaged during testing.
- Engine/exhaust/ignition are synthetic effects, not OEM recordings or ECU readings. Acoustic fidelity has not been compared against real recordings, and real-phone road/battery/audio-device testing remains outstanding.

Standalone suite source is `tests/V4SoundCheck.java`. The default instrumentation runner is `V4Verification`; select the older GPS suite with the quoted Gradle property `'-PverificationRunner=com.onesecondx.velocity.MotoVerification'` when assembling its test APK. Use a disposable emulator because these tests reset the active test trip.

## Moto 2.0

- Android native debug build and lint pass with no errors. Target remains SDK 34; this artifact is intended for direct test installation, not Play Store publication.
- Custom dependency-free Android instrumentation runner: **39 checks passed** on API 35. Covers GPS speed conversion, geodesic distance, route sampling, rejection of stale and poor-accuracy fixes, manual laps, paused clock, GPX namespace/XML point count, exact GPX output bytes, CSV SI-unit columns, and CSV file creation.
- All 12 motorcycle styles rendered natively in both day and night: 24 PNG snapshots, marked DEMO. Layout inspected for the four requested sport models and the additional street/scooter/analog variants.
- UI checked at 720 × 1280 / 320 dpi in portrait and 1280 × 720 in landscape. Landscape enters immersive full screen with four large bottom controls. Model selector changed from S 1000 RR to Ninja H2R and saved the model preference.
- Portrait layout avoids duplicated original-dashboard controls on motorcycle styles; start/pause and finish remain anchored below content.
- Fixed RPM/gear label overlap, Z900 needle/value overlap, unit placement on the Wave dial, day-mode contrast, and space between the R1 unit and engine caption. Three-digit speed readouts reduce their font size.
- GPX/CSV document writing is exercised through the app's Activity result handler using local files in the test sandbox. System file picker launch and cancel are also exercised; every cloud/document provider has not been tested.
- No background tracking or ECU connectivity added. RPM and gear simulation is explicitly limited to DEMO. Real engine channels remain unavailable. OEM-identical, model-year-specific rendering is not claimed.

Run instrumentation on a disposable emulator, since it resets the active test trip:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.onesecondx.velocity.test/com.onesecondx.velocity.MotoVerification
```

The earlier 1.0 checks are retained below.

- `gradlew.bat assembleDebug lintDebug`: successful, no lint errors. Warnings concern target SDK, localization of native text, and optional platform metadata.
- APK signature verified using Android SDK apksigner (v2).
- Installed and launched on Android 15 / API 35 emulator.
- Visually inspected dashboard and settings at 1080 × 1920; moved start/pause and finish controls outside scrolling content so they remain reachable.
- Runtime location permission dialog appears on first start; precise foreground permission resumes GPS subscription.
- Injected Android emulator GPS coordinates with 43.1965 knots; GPS delivered 22.22398 m/s and app recorded a maximum of 80 km/h. Test trip saved 21.81 m and 31 seconds. This is simulated input through the Android location provider, not a physical road test.
- Trip history persisted after app restart. Changed units to mph and accent to cyan; both persisted after force-stop/relaunch.
- Demo displayed an explicit simulated-data label and changing speed. Finishing the demo left the existing one-trip history unchanged.
- Started GPS, sent app to Home, waited for Home UI to settle, returned to app: start button and ready state confirmed automatic pause.

Physical phone GPS accuracy, battery consumption, vibration hardware, and windshield HUD visibility have not been road-tested. Tracking is intentionally foreground-only. Distribution artifact is a debug APK, not a Play Store release.
Also visually checked all three dashboard styles at 720 × 1280 (360 dp width), including mirrored HUD. Primary controls stayed visible.

