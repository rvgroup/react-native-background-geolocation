# AndroidX / Jetifier-free integration

## Dependency changes

The common module owns these runtime dependencies directly:

| Coordinate | Purpose |
| --- | --- |
| `androidx.core:core:1.9.0` | ContextCompat, ActivityCompat, NotificationCompat, utilities |
| `androidx.appcompat:appcompat:1.6.1` | Transparent permission request activity/theme |
| `androidx.localbroadcastmanager:localbroadcastmanager:1.1.0` | Permission results and existing geolocation broadcasts |
| `com.google.android.gms:play-services-location:17.0.0` | First AndroidX generation retaining the legacy location API used here |

Gradle may select higher AndroidX versions required by the host application.
The former `11+` Play Services default brought in Support Library transitively;
the default is now fixed. Existing Play Services version overrides still work,
but must select an AndroidX-compatible version with the location APIs used here.

`io.github.nishkarsh:android-permissions:0.1.7` is removed. Its API-compatible
AndroidX sources are embedded in `common`, so no new artifact repository or
application dependency is needed. See [provenance, license and exact changes](common/vendor/android-permissions/README.md).
The sources are pinned by the geolocation commit; there is no separate published
permission artifact or external fork to resolve.

`BackgroundGeolocationFacade.start()` and `LocationManager.getCurrentLocation()`
continue to use `PermissionManager` and the original granted/denied callbacks.
The public JS API is preserved. Subsequent async and lifecycle fixes are described
in [Android modernization](MODERNIZATION.md).

## Application migration

1. Pin the geolocation package to the commit containing this change:
   `git+https://github.com/rvgroup/react-native-background-geolocation.git#<commit>`.
   Update and commit the application's package-manager lockfile.
2. In the application's `android/gradle.properties` set:

   ```properties
   android.useAndroidX=true
   android.enableJetifier=false
   ```

3. Remove workaround blocks that inject `core`, `appcompat` or
   `localbroadcastmanager` into
   `:@thekingzoom_react-native-background-geolocation-common`. These dependencies
   are now declared in the module itself.
4. Remove `com.android.support` exclusions introduced specifically for this
   library and the obsolete `supportLibVersion` override. Other application
   dependencies may need independent migration.
5. Remove any separately added `android-permissions` dependency or substitution
   for it: the permission classes are now part of common. Remove a `11+` or other
   pre-AndroidX Play Services override if present, allowing the pinned default.
6. Clean and build both application variants with Jetifier disabled. Check the
   dependency report for legacy dependencies from other packages.

## Reproducible build verification

`verification/` is a minimal Android APK consumer of both library modules. It
uses AGP **8.13.2**, React Android **0.87.1**, min SDK **24**, compile SDK **37** and
target SDK **36**. It adds no AndroidX dependencies or Support Library exclusions.
It is a build/linking fixture, not a runnable React Native example application.

With JDK **17** and `ANDROID_HOME` pointing to an SDK containing API 37
(`sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'`):

```sh
bash android/gradlew -p android/verification :app:assembleDebug :app:assembleRelease :app:verifyAndroidXDependencies :@thekingzoom_react-native-background-geolocation-common:testDebugUnitTest :@thekingzoom_react-native-background-geolocation-common:assembleDebug :@thekingzoom_react-native-background-geolocation-common:assembleRelease
```

The APK builds include `checkDebugDuplicateClasses` / `checkReleaseDuplicateClasses`.
`verifyAndroidXDependencies` rejects Support Library coordinates and the removed
permission AAR in both consumer runtime graphs. Artifact paths are written under
`android/verification/app/build/verification/`.

Scan the resulting common AARs (including nested bytecode and resources):

```sh
python scripts/verify-androidx-bytecode.py android/common/build/outputs/aar/@thekingzoom_react-native-background-geolocation-common-debug.aar android/common/build/outputs/aar/@thekingzoom_react-native-background-geolocation-common-release.aar
```

The scanner exits nonzero on old package references and can also inspect an
individual JAR. It is intentionally strict for this library's own artifacts.
Scanning every third-party AndroidX artifact can match preserved protocol keys,
AIDL interfaces and parcelizers in official AndroidX itself; those are not a
dependency on the old Support Library and do not require Jetifier.

On Windows use `android\gradlew.bat` instead of `bash android/gradlew`.
The standalone library build uses the same AGP **8.13.2** / Gradle **8.13** and
React Android **0.87.1** defaults. Hosts can set `reactNativeLibVersion` or select
React Android through their RN Gradle plugin. The wrapper distribution has a
pinned SHA-256. CI runs these checks in `.github/workflows/android.yml`.

### Results (2026-11-05)

- Standalone library debug/release builds: passed, Jetifier disabled.
- Verification consumer debug/release APK builds: passed with the versions above
  (Gradle 8.13, JDK 17). Both duplicate-class checks passed. Release is an
  unsigned fixture with R8 and resource shrinking enabled; its rules preserve RN
  module entry points so the geolocation implementation is analyzed.
- Debug/release dependency-graph checks: passed, no Support Library coordinates
  or old android-permissions artifact.
- Common debug/release AAR scans: passed, zero legacy references; permission
  classes and their MIT license are included in both AARs.
- Runtime-artifact audit: inspected 97 debug artifacts including common AARs.
  String matches outside this library occur only inside official AndroidX core,
  emoji2 and versionedparcelable compatibility data/classes.
- Negative control: the scanner correctly rejects the original 0.1.7 AAR,
  detecting references in five permission-library classes.
- Regression tests: **26 passed**, no failures or skipped tests (10 permission,
  5 current-location, 8 worker, 2 deduplication, 1 PendingIntent).
- Robolectric coverage includes API 24/30 for workers, API 28 for current-location
  requests and API 34 for PendingIntent construction. No device/emulator runtime
  or actual RN instance was exercised.

AGP 8.13.2 emits its standard warning that compile SDK 37 is newer than its
tested maximum (36.1); builds completed without suppressing that warning.

## Runtime verification in the host application

The permission unit tests exercise queue/callback logic with mocks:
already granted/repeated requests, first foreground request, partial grants,
denial followed by grant, concurrent callers, interruption/retry, background
notification routing, notification dismissal and immediate retry from a denial
callback. They do not simulate the Android
permission UI or prove that GPS runs in the background.

On the application's supported Android versions (at least API 24 and API 36),
run these checks in debug and release:

| Scenario | Expected result |
| --- | --- |
| Permissions already granted; `start()` | No permission prompt or class-loading failure; location events arrive |
| Fresh install; `start()`; grant precise location | Permission activity closes, service starts, location events arrive |
| Fresh install; deny; retry and grant | Denial callback is delivered; later request succeeds |
| Permanently denied; grant in system settings; retry | Granted path works without a stale pending request |
| `getCurrentLocation()` with granted and denied permissions | Promise resolves or returns the existing permission-denied error |
| Stop then start again | Service stops and restarts; events resume |
| Start in foreground, then send app to background | Foreground-service notification remains and location events continue |

Record OS/API, device, location provider, permissions and build variant for each
run. For background tests configure the existing foreground-service behavior and
grant the permissions required by that Android version. This migration does not
add background-location or notification permission requests to the JS API.

Runtime tests on a physical device/emulator and integration into the user's real
application were not performed: the requested verification scope was limited to
the library and a separate build consumer.
