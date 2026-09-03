# Announcement draft: Phone MCP AppFunctions developer preview

## Phone MCP now works both ways on Android

Phone MCP already lets an AI client connect to your phone over MCP. In the next release, it can
also publish its first-party capabilities directly to Android as AppFunctions, allowing authorized
on-device agents to discover and invoke them locally.

On Android 16 and newer, the developer preview includes:

- searching contacts;
- taking a photo;
- getting the phone's current location;
- reading a one-shot sensor snapshot;
- listing files, inspecting metadata, and reading bounded file content;
- preparing an SMS for review; and
- in the Full build only, sending an SMS directly when the user has granted SMS permission.

There is no server URL or bearer token to configure for these on-device calls. Android controls
which agents may invoke AppFunctions. Phone MCP's existing tool switches control access through
both MCP and AppFunctions, while Android continues to enforce the relevant runtime permissions.
Preparing an SMS never sends it automatically. The Full build's direct-SMS action is clearly
identified because it sends immediately and may incur carrier charges.

AppFunctions are still experimental. Android currently limits end-to-end assistant integration,
including Gemini integration, to selected testers. Developers can install the release on a
supported device and verify every published function today with Android's `adb shell cmd
app_function` tooling.

Read the setup, security, and testing guide:
https://github.com/premex-ab/phone-mcp/blob/main/docs/appfunctions.md

Android AppFunctions overview:
https://developer.android.com/ai/appfunctions
