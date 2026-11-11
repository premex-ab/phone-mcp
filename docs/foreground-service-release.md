# Foreground service release notes

The MCP server serves interactive requests over HTTP/SSE and the optional remote
tunnel. It is not a bounded data synchronization job. On Android 14+, its base
foreground service type is `specialUse`; `camera` is added only when camera
permission is granted. Older Android versions use no base type and retain the
optional camera type where supported.

Android 15 limits background `dataSync` foreground services to six hours per
24-hour period. Both reported crashes occurred when foreground promotion tried
to use an exhausted quota, including the old camera fallback. Neither startup
path now uses `dataSync`.

On Android 14+, BootReceiver posts the existing tap-to-restart notification when
the user previously left the server running. It does not request a service start.
The user must allow notifications to see this reminder; otherwise they can open
the app and start the server manually. Earlier Android versions retain automatic
boot restoration. A catch in BootReceiver cannot catch failures that occur later
in Service.onCreate.

## Play Console declaration

Before releasing the updated bundle, replace the data sync foreground-service
declaration with Special use in Policy > App content, retaining the camera
declaration. Suggested explanation, matching the manifest:

> User-started MCP server that keeps an authenticated HTTP/SSE endpoint and
> optional remote-access tunnel available to AI clients while the app is in the
> background. The ongoing notification shows server status and opens the app,
> where the user can stop the server. Requests arrive interactively, so this
> cannot be scheduled as deferred data synchronization.

Google Play reviews special-use declarations; changing the manifest does not
itself establish Play approval.

## Verification

Run the app's unit tests, lint, and device regression tests:

```sh
./gradlew :app:assemblePlayDebug :app:testPlayDebugUnitTest :app:lintPlayDebug
./gradlew :app:connectedPlayDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=se.premex.mcp.McpServerServiceTest
```

The device tests check the packaged service type and permissions, boot handling,
and an authenticated endpoint remaining available after repeated starts and
moving the activity into the background. Use a test device with the default
server port (3001).

For release verification on Android 15+, grant camera and notification
permissions, start the server, and reboot. Confirm a restart reminder appears
without the server starting; tapping it should start the server, including camera
support. Also verify a manual start with camera permission denied. Check the
running service's foreground type with `adb shell dumpsys activity services
se.premex.mcp`: it should include special use and optionally camera, never data
sync. Check an Android 13 device for automatic boot restoration.

References: [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
[data sync timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout),
[Android 15 boot restrictions](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed).
