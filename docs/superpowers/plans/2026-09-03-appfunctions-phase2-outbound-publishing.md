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
