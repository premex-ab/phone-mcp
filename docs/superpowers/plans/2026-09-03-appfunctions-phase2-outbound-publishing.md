# AppFunctions Phase 2 — Outbound Publishing

## Goal

Publish selected Phone MCP capabilities as native Android AppFunctions so authorized
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

The Play flavor intentionally excludes direct SMS and its restricted permission. AppFunction
factory registrations are Hilt multibindings contributed by each tool module, so the generated
inventory follows the dependency graph of each flavor automatically.

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
which on-device callers may invoke them. Each function checks its relevant runtime permission
and reports `AppFunctionPermissionRequiredException` when permission is absent. Direct SMS is
described explicitly as immediate and potentially chargeable; SMS intent is described as
requiring user review.

## Verification

1. Run `./gradlew test`.
2. Run `./gradlew lint :app:assembleFullDebug :app:assemblePlayDebug`.
3. Confirm full metadata contains all four function IDs.
4. Confirm Play metadata contains `prepareSms`, `searchContacts`, and `takePhoto`, but not
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

- Android indexed all four functions with static and runtime metadata.
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
