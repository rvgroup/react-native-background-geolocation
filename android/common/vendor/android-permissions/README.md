# android-permissions: embedded AndroidX fork

Source: [nishkarsh/android-permissions v0.1.7](https://github.com/nishkarsh/android-permissions/tree/78ecc850b4d5055a2dd5ce41cf7ebea3354a5069),
commit `78ecc850b4d5055a2dd5ce41cf7ebea3354a5069`. MIT license:
[LICENSE.android-permissions](src/main/resources/META-INF/LICENSE.android-permissions)
(also packaged in the AAR).

These sources are compiled directly into the geolocation `common` module. There
is no separate Maven/JitPack artifact or additional Gradle project to install.
Pinning the geolocation Git commit also pins these sources. Do not add the old
`io.github.nishkarsh:android-permissions` artifact alongside them (duplicate classes).

The published AndroidX release 2.1.8 changes `onPermissionDenied()` to
`onPermissionDenied(DeniedPermissions)` and changes denial handling. This fork
retains the 0.1.7 API and permission queue/callback algorithm instead.

Changes from the pinned upstream sources:

- All Support Library imports migrated: annotations, ContextCompat,
  ActivityCompat, AppCompatActivity, LocalBroadcastManager and NotificationCompat.
- `R` is imported from the containing common module. Permission intent extra
  strings retain their original values, independent of the host namespace.
- Info logging remains disabled, as in the original release AAR (`DEBUG=false`),
  without requiring a generated vendor BuildConfig.
- The permission activity and notification dismissal receiver are declared as
  non-exported components in the common manifest. The unused application label
  and `app_name` resource are not copied into the host application.
- Notification pending intents specify immutability (required for target 31+);
  the existing notification channel ID is registered on API 26+. These retain
  the background permission-notification path on current Android versions.
- PermissionHandler regression tests cover callbacks, concurrent callers,
  partially granted permissions, denial/retry, interruption and notification
  routing. Subsequent reliability fixes serialize permission entry on the main
  thread and remove completed requests before callbacks, allowing a callback to
  request permissions again without corrupting the pending queue.

Runtime notification permission on Android 13+ and background-location/foreground
service requirements remain the responsibility of the host application, just as
for the geolocation service itself.
