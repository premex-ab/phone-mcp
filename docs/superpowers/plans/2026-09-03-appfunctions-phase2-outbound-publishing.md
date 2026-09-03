# AppFunctions Phase 2 — Outbound Publishing

## Goal

Publish Phone MCP's suitable first-party capabilities as native Android AppFunctions so authorized
on-device agents can invoke them without connecting to the MCP-over-SSE server.

This completes the second and final phase described in
[`2026-05-27-appfunctions-integration-design.md`](../specs/2026-05-27-appfunctions-integration-design.md).

## Published functions

| Function | Module | Behavior | Distribution |
|---|---|---|---|
| `sendSms` | `tools/sms` | Sends an SMS immediately through the carrier. Requires `SEND_SMS`. | Full only |
| `prepareSms` | `tools/smsintent` | Opens or notifies the SMS composer for user review; never sends automatically. | Full and Play |
| `searchContacts` | `tools/contacts` | Searches contact phone numbers by partial name. Requires `READ_CONTACTS`. | Full and Play |
| `takePhoto` | `tools/camera` | Captures a JPEG with a selected lens and returns a temporary content URI. Requires `CAMERA`. | Full and Play |
| `getCurrentLocation` | `tools/location` | Returns the freshest available GPS/network position. Requires location permission. | Full and Play |
| `getSensorSnapshot` | `tools/sensor` | Returns a one-shot snapshot of available sensors and cached readings. | Full and Play |
| `listFiles`, `getFileInfo`, `readFile` | `tools/files` | Browses virtual roots and returns bounded UTF-8 or base64 file content. | Full and Play |

The Play flavor intentionally excludes direct SMS and its restricted permission. AppFunction
factory registrations are Hilt multibindings contributed by each tool module, so the generated
inventory follows the dependency graph of each flavor automatically.

Every published function is disabled in static metadata. At application startup, Phone MCP uses
`setAppFunctionEnabled` to synchronize each function with its existing tool switch. Direct SMS
and SMS drafting have distinct tool IDs; legacy preferences are migrated to preserve prior choices.

## Build and runtime structure

- `mcp.android.appfunctions` adds the runtime, service, and KSP compiler dependencies.
- Library modules generate contribution metadata for their annotated functions.
- The app module sets `appfunctions:aggregateAppFunctions=true` and generates the service,
  invoker, inventory, and `assets/app_functions_v2.xml`.
- `McpServerApplication` collects flavor-specific factory registrations and implements
  `AppFunctionConfiguration.Provider` for Hilt-created function classes.
- `app_metadata.xml` describes the cross-function behavior and security boundaries.
- The camera module exposes captured cache files through a non-exported `FileProvider`.

## Security model

AppFunctions calls do not pass through the MCP server or its bearer token. Android decides
which on-device callers may invoke them. Phone MCP's tool switches enable and disable the same
capability through both MCP and AppFunctions. Each function checks its relevant runtime permission
and reports `AppFunctionPermissionRequiredException` when permission is absent. Direct SMS is
described explicitly as immediate and potentially chargeable; SMS intent is described as
requiring user review.

## Verification

1. Run `./gradlew test`.
2. Run `./gradlew lint :app:assembleFullDebug :app:assemblePlayDebug`.
3. Confirm full metadata contains all nine function IDs.
4. Confirm Play metadata contains all eight safe function IDs, but not
   `sendSms`.
5. On an Android 16+ device, install the desired flavor and run:

   ```shell
   adb shell cmd app_function list-app-functions | grep --after-context 12 se.premex.mcp
   ```

6. Invoke non-destructive functions first (`searchContacts`, `prepareSms`), then test camera
   and direct SMS only with explicit test recipients and user awareness.

## Documentation and announcement

- The README introduces the feature, flavor split, permissions, and experimental availability.
- [`docs/appfunctions.md`](../../appfunctions.md) is the public usage, security, and ADB testing
  guide.
- [`docs/announcements/appfunctions-developer-preview.md`](../../announcements/appfunctions-developer-preview.md)
  contains release-announcement copy. Keep the experimental/private-preview caveat until Android
  makes end-to-end assistant access generally available.

## Physical-device verification

Verified on a Pixel 10 Pro XL running API 37 on 2026-09-03 with the Full debug build:

- Android indexed the original four functions with static and runtime metadata. The expanded
  inventory is covered by generated-metadata checks: nine functions in Full and eight in Play.
- Contact search returned the intended permission error before permission was granted, then an
  empty list for a deliberately nonexistent contact.
- Camera validation rejected an invalid quality, then a real capture returned a cached JPEG
  content URI.
- SMS preparation opened Google Messages with an unsent test draft and returned success.
- Direct SMS returned the intended permission error while `SEND_SMS` remained denied; no message
  was sent.

The Play APK could not replace the installed Play app during this session because the installed
version code was newer. Play/Full function separation remains covered by generated-metadata
tests and build verification.

The expanded Full build was also installed on an Android 16.1 emulator. Android indexed all nine
functions, the discovery card listed the seven matching tool switches, the file-access warning
appeared as intended, and the enabled switch persisted after an app restart. Native invocation
could not be validated on that system image because its dispatcher timed out for both Phone MCP
and Android's own permission-controller AppFunction. Switch persistence remains independent from
platform synchronization, and platform updates are bounded so an unhealthy dispatcher cannot
stall app preferences.
