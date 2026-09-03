# Announcement draft: Phone MCP AppFunctions developer preview

## Phone MCP now works both ways on Android

Phone MCP already lets an AI client connect to your phone over MCP. In the next release, it can
also publish selected capabilities directly to Android as AppFunctions, allowing authorized
on-device agents to discover and invoke them locally.

On Android 16 and newer, the developer preview includes:

- searching contacts;
- taking a photo;
- preparing an SMS for review; and
- in the Full build only, sending an SMS directly when the user has granted SMS permission.

There is no server URL or bearer token to configure for these on-device calls. Android controls
which agents may invoke AppFunctions, while Phone MCP continues to enforce the relevant Contacts,
Camera, Notifications, and SMS permissions. Preparing an SMS never sends it automatically. The
Full build's direct-SMS action is clearly identified because it sends immediately and may incur
carrier charges.

AppFunctions are still experimental. Android currently limits end-to-end assistant integration,
including Gemini integration, to selected testers. Developers can install the release on a
supported device and verify every published function today with Android's `adb shell cmd
app_function` tooling.

Read the setup, security, and testing guide:
https://github.com/premex-ab/phone-mcp/blob/main/docs/appfunctions.md

Android AppFunctions overview:
https://developer.android.com/ai/appfunctions
