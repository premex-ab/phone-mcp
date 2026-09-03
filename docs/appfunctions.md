# Phone MCP AppFunctions

Phone MCP can expose its first-party phone capabilities as native Android AppFunctions. This lets an
authorized on-device agent discover and invoke them locally, without starting Phone MCP's SSE
server or configuring an MCP client.

AppFunctions are currently an experimental Android feature. They require Android 16 or newer,
and end-to-end access from system agents such as Gemini is still limited to selected testers.
The functions can nevertheless be inspected and tested directly with ADB on a supported device.

## Available functions

| Capability | Function identifier | Play | Full | User-visible effect |
|---|---|:---:|:---:|---|
| Search contacts | `se.premex.mcp.contacts.appfunctions.ContactsAppFunctions#searchContacts` | Yes | Yes | Returns matching names and phone numbers |
| Take a photo | `se.premex.mcp.camera.appfunctions.CameraAppFunctions#takePhoto` | Yes | Yes | Captures a JPEG and returns a temporary content URI |
| Get location | `se.premex.mcp.location.appfunctions.LocationAppFunctions#getCurrentLocation` | Yes | Yes | Returns the freshest available geographic position |
| Get sensor snapshot | `se.premex.mcp.sensor.appfunctions.SensorAppFunctions#getSensorSnapshot` | Yes | Yes | Returns available sensors and their latest cached readings |
| List files | `se.premex.mcp.files.appfunctions.FilesAppFunctions#listFiles` | Yes | Yes | Lists storage roots or a directory's entries |
| Get file info | `se.premex.mcp.files.appfunctions.FilesAppFunctions#getFileInfo` | Yes | Yes | Returns bounded file or directory metadata |
| Read file | `se.premex.mcp.files.appfunctions.FilesAppFunctions#readFile` | Yes | Yes | Returns at most 250 KB as UTF-8 or base64 |
| Prepare an SMS | `se.premex.mcp.smsintent.appfunctions.SmsIntentAppFunctions#prepareSms` | Yes | Yes | Opens the SMS composer or posts a notification; never sends automatically |
| Send an SMS | `se.premex.mcp.sms.appfunctions.SmsAppFunctions#sendSms` | No | Yes | Sends immediately through the carrier and may incur charges |

The Play package is `se.premex.mcp`. The Full package is `se.premex.mcp.full`.

## How users invoke them

When an installed assistant supports Android AppFunctions and is authorized by the operating
system, a user can make requests such as:

- "Find Ada in my contacts."
- "Take a photo with the back camera."
- "Where is this phone right now?"
- "Show me the latest accelerometer reading."
- "List the files in my Downloads folder."
- "Draft a text to +46 70 123 45 67 saying that I am running late."

The exact wording and availability depend on the assistant. During the experimental preview,
ADB is the reliable way to verify the integration.

Phone MCP shows an **On-device assistants** card on its home screen. Open **How it works** to see
whether the phone supports AppFunctions, which actions the installed flavor publishes, how
authorization differs from the MCP server, and a shortcut to Android's permission settings. The
card can be dismissed with its close button and restored from **Settings → On-device assistants**.

## Permissions and trust model

AppFunction calls do **not** pass through Phone MCP's SSE server, so they do not use its bearer
token. Android decides which caller apps may discover and execute AppFunctions. Phone MCP keeps
each function's Android enabled state synchronized with the same tool switch used for MCP: a
disabled tool is unavailable through both interfaces.

Phone MCP still checks the relevant Android runtime permission for every invocation:

- Contact search requires Contacts permission.
- Photo capture requires Camera permission.
- Current location requires coarse or fine Location permission.
- Sensor snapshots require no additional runtime permission for standard sensors.
- Shared file access requires the applicable Android media or storage permissions.
- Preparing an SMS requires Notifications permission on Android 13 and newer.
- Direct SMS requires SMS permission and exists only in the Full build.

Preparing an SMS always requires the user to review and send it in their messaging app. Direct
SMS is different: it sends immediately, can incur carrier charges, and should only be invoked
after the user has explicitly requested it.

Returned photos are temporary files in Phone MCP's cache. Callers receive a content URI rather
than unrestricted access to app storage. File reads are capped at 250 KB per AppFunction call to
stay below Android IPC limits; larger files report that the result was truncated.

## Test on a connected device

Use a device running Android 16 or newer with USB debugging enabled. Replace `PACKAGE` below
with `se.premex.mcp` for Play or `se.premex.mcp.full` for Full.

First check that the platform service and Phone MCP functions are registered. The current Android
shell command lists every package, so filter its JSON output to the Phone MCP package:

```shell
adb shell cmd app_function help
adb shell cmd app_function list-app-functions | jq --arg package PACKAGE '.[$package]'
```

The listing should contain eight functions for Play and nine for Full. Open Phone MCP and enable
the corresponding tool switch before executing a function. The following calls are
ordered from least to most user-visible.

Inspect the device's sensor snapshot without requesting a dangerous permission:

```shell
adb shell "cmd app_function execute-app-function \
  --package PACKAGE \
  --function 'se.premex.mcp.sensor.appfunctions.SensorAppFunctions#getSensorSnapshot' \
  --parameters '{}'"
```

List Phone MCP's available storage roots without reading file content:

```shell
adb shell "cmd app_function execute-app-function \
  --package PACKAGE \
  --function 'se.premex.mcp.files.appfunctions.FilesAppFunctions#listFiles' \
  --parameters '{}'"
```

Search for a deliberately nonexistent contact to exercise the success path without printing
address-book data:

```shell
adb shell "cmd app_function execute-app-function \
  --package PACKAGE \
  --function 'se.premex.mcp.contacts.appfunctions.ContactsAppFunctions#searchContacts' \
  --parameters '{\"name\":\"PhoneMcpNoSuchContact987654321\"}'"
```

With Contacts permission granted, the expected return value is an empty list. Without it, the
function should return a permission-required error.

Verify camera argument validation without taking a photo:

```shell
adb shell "cmd app_function execute-app-function \
  --package PACKAGE \
  --function 'se.premex.mcp.camera.appfunctions.CameraAppFunctions#takePhoto' \
  --parameters '{\"lens\":\"back\",\"quality\":0}'"
```

The expected result is an invalid-argument error. To perform a real capture after informing the
user, retry with a quality from 1 through 100; success returns a `content://` URI.

Prepare, but do not send, a clearly labeled test SMS:

```shell
adb shell "cmd app_function execute-app-function \
  --package PACKAGE \
  --function 'se.premex.mcp.smsintent.appfunctions.SmsIntentAppFunctions#prepareSms' \
  --parameters '{\"phoneNumber\":\"+46700000000\",\"message\":\"Phone MCP AppFunctions test - do not send\"}'"
```

This should open the default messaging app or create a notification. Back out of the draft when
the test is complete.

Do not smoke-test direct SMS with a real recipient. In the Full build, leaving SMS permission
denied is a safe way to verify its guard:

```shell
adb shell "cmd app_function execute-app-function \
  --package se.premex.mcp.full \
  --function 'se.premex.mcp.sms.appfunctions.SmsAppFunctions#sendSms' \
  --parameters '{\"phoneNumber\":\"+46700000000\",\"message\":\"This must not send\"}'"
```

The expected result is a permission-required error and no carrier action.

## Troubleshooting

- `Can't find service: app_function`: the device does not provide the AppFunctions service.
- No Phone MCP functions are listed: confirm that the current build was installed, launch it
  once, and rerun the listing command after Android finishes indexing the package.
- Permission-required error: grant the named permission from Phone MCP's app settings, then
  retry.
- Function-disabled error: enable the matching tool switch in Phone MCP; the change applies to
  both AppFunctions and MCP.
- Every invocation times out, including functions from Android system packages: the emulator or
  system image's AppFunctions dispatcher is unhealthy. Try an updated system image or a physical
  Android 16+ device before attributing the failure to Phone MCP.
- Assistant cannot discover the functions although ADB can: agent integration remains limited
  during Android's experimental preview.

For implementation details, see the
[Phase 2 publishing note](superpowers/plans/2026-09-03-appfunctions-phase2-outbound-publishing.md).
