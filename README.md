# Stube
## Performance benchmarks

### Setup
- `:benchmark` is a `com.android.test` module with Macrobenchmark 1.5.0 and the Baseline Profile plugin 1.5.0.
- The plugin adds the `benchmarkRelease` and `nonMinifiedRelease` build types. Both install as `com.stube.app.benchmark`, so your installed app and its data are not touched.
- `app/src/bench` is compiled only into those two build types. It seeds 500 bookmarks, 200 finished downloads and 40 dummy passwords, and opens panels.
- There is no Gradle wrapper in this repository: use Gradle 9.4.1 (as CI does), JDK 17 or newer, and an Android SDK with API 37.1.

### Device
- Physical arm64-v8a phone (the app ships only that ABI), Android 15 (API 35) or newer, USB debugging on. Do not use emulator numbers.
- Only one device connected, unlocked, screen on.
- Battery above 50%, device cool, battery saver off, fixed brightness and refresh-rate setting.
- Airplane mode on, so YouTube loading does not add network noise.
- Keep system animation settings the same between runs.

### Commands
Cold startup:

    gradle :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stube.benchmark.StartupBenchmark

Library scroll:

    gradle :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stube.benchmark.ScrollBenchmark#libraryScroll

Downloads scroll:

    gradle :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stube.benchmark.ScrollBenchmark#downloadsScroll

Generate the Baseline Profile (do this before any benchmark that requires it):

    gradle :app:generateReleaseBaselineProfile

The output goes to `app/src/release/generated/baselineProfiles/`. Review and commit it.

### Results
Results are printed in the test output. JSON and Perfetto traces are under `benchmark/build/outputs/`.

- `timeToInitialDisplayMs`: launch to first frame (the splash), not to a loaded YouTube page. Compare medians. Lower is better.
- `coldStartupNoCompilation` against `coldStartupBaselineProfile`: the gain from the profile.
- `frameDurationCpuMs` P50, P90, P95, P99: CPU time per frame.
- `frameOverrunMs`: how far past its deadline a frame finished. Positive values are jank, negative values are headroom.
- Compare runs only from the same device and conditions. Repeat before trusting differences of a few percent.

### Baseline Profile coverage
- `app/src/main/baseline-prof.txt` is a hand-written wildcard file. By pattern it matches all `com.nova.browser` classes, but this has not been verified on a device. It does not list GeckoView or NewPipe.
- The generator exercises cold start, Library, Downloads, Passwords and Settings. It does not exercise the mini player, the options sheet, the download sheet or video playback.
- After generating, check the output for `LibraryScreen`, `DownloadsScreen`, `PasswordsScreen` and `SettingsScreen` before claiming coverage.
