# Android modernization — 2026-11-05

## 1. Location request and worker reliability

- Permission callbacks no longer block the main thread while waiting for GPS.
- Current-location requests remove their Android listener on success, timeout and
  interruption. Cancelling a facade wait cancels its underlying request.
- A start permission result arriving after `stop()` or facade destruction is ignored.
- Worker location requests work below API 30 through AndroidX, have a 30-second
  timeout and support cancellation. Missing providers/null results/transient
  failures retry; permission failures terminate that work.
- Persisted generation tokens reject callbacks from earlier tracking sessions.
  Follow-up work is appended without replacing the running worker.

WorkManager's one-minute delay is best effort. It neither guarantees one-minute
GPS delivery nor bypasses Android background-location restrictions.

## 2. Bounded resources and RN lifecycle

- Native operations use two threads and a queue of at most 64 operations.
  Current-location waits use a separate pool with at most four concurrent requests
  and no queue. Saturation returns an error through the existing callback shape.
- Module invalidation interrupts tasks, removes lifecycle registration and clears
  deduplication state. Events/callbacks are guarded against an inactive RN instance.
- Location time-bucket deduplication is atomic and bounded to 2,048 entries. Very
  old buckets can be delivered again after eviction.
- Mutable provider PendingIntents are scoped to the application's package and no
  longer use `FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT`.
- Permission entry is dispatched to the main thread. Completed listener batches
  are removed before callbacks, supporting immediate retry from a denial callback.
- Work status helpers consider all returned records and preserve interruption.

## 3. Build and continuous verification

- Standalone defaults: JDK 17, Gradle 8.13 (distribution checksum pinned),
  AGP 8.13.2, React Android 0.87.1, minSdk 24, compileSdk 37, targetSdk 36.
  The API 24 minimum is a compatibility change; host overrides still exist but
  lower Android versions are outside this verification baseline.
- Removed JCenter, local Maven and obsolete RN Maven-directory lookup from the
  standalone build; common no longer injects its own AGP buildscript.
- Consumer R8 rules retain headless task runners instantiated by stored class name.
- The separate consumer builds debug and R8/resource-shrunk release APKs with
  Jetifier disabled. CI also runs regression tests, checks dependency graphs,
  scans common AAR bytecode and uploads test/build reports.

See [commands and results](ANDROIDX.md). Local regression suite: **26 passing
tests**. The GitHub Actions workflow requires a remote run before CI status can
be claimed. AGP warns that compileSdk 37 exceeds its tested maximum of 36.1.

## Next increments

1. Foreground-service startup/error handling, reboot and `stopOnTerminate` state
   consistency, including modern Android permission restrictions.
2. Replace legacy GoogleApiClient location/activity APIs, then update their pinned
   Play Services dependencies with provider-specific regression coverage.
3. Verify RN instance recreation/New Architecture in a runnable RN consumer;
   modernize the native module only against that runtime fixture.
4. Exercise background GPS, permission dialogs, process death and reboot on
   supported Android devices/emulators. Review iOS separately with macOS tooling.

This stage verifies the library and a separate build consumer. It does not claim
device background tracking, RN runtime or iOS validation. Public JS method
signatures and event names are preserved.
