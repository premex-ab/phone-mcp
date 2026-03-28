# UI Automation Tool — Design Spec

## Overview

Add a `tools/uiautomation/` module that exposes the full Android UI to AI assistants via an `AccessibilityService`. The AI can read screen content (accessibility tree + screenshots) and perform actions (tap, swipe, type, press keys, scroll) on any app — turning the phone into a computer-use agent.

## Approach

**AccessibilityService + Tool Module (Approach 1)**

A custom `AccessibilityService` provides runtime access to the screen's accessibility node tree, gesture dispatch, and screenshots. A standard tool module wraps this in MCP tools following the configurator pattern. No root, no external dependencies, pure Android APIs.

Screenshots use `AccessibilityService.takeScreenshot()` which requires API 30+. On older devices, screenshots are unavailable but the accessibility tree still works.

## Module Structure

```
tools/uiautomation/
├── build.gradle.kts                          # mcp.android.tool convention plugin
├── src/main/
│   ├── AndroidManifest.xml                   # AccessibilityService declaration
│   ├── res/xml/
│   │   └── accessibility_service_config.xml  # Service capabilities config
│   ├── res/values/
│   │   └── strings.xml                       # Service description string
│   └── java/se/premex/mcp/uiautomation/
│       ├── service/
│       │   ├── PhoneAccessibilityService.kt
│       │   └── AccessibilityServiceConnection.kt
│       ├── configurator/
│       │   ├── UiAutomationToolConfigurator.kt
│       │   └── UiAutomationToolConfiguratorImpl.kt
│       ├── di/
│       │   └── UiAutomationToolModule.kt
│       └── tool/
│           └── UiAutomationTool.kt
```

### Key Components

**PhoneAccessibilityService** — Extends `AccessibilityService`. On `onServiceConnected()`, stores itself in `AccessibilityServiceConnection`. Serves as the entry point for all screen reading and interaction.

**AccessibilityServiceConnection** — Singleton holder (companion object) that stores the active service instance. Tools check `AccessibilityServiceConnection.instance` to verify the service is running and to access `rootInActiveWindow`, `performGlobalAction()`, `dispatchGesture()`, and `takeScreenshot()`.

**UiAutomationToolConfiguratorImpl** — Registers all MCP tools with the server via `server.addTool()`.

**UiAutomationTool** — Implements `McpTool`. Declares no Android permissions (accessibility service is enabled via Settings, not runtime permissions). Uses a strong `disclaim` message about full screen access.

### Manifest Declaration

The `AccessibilityService` is declared in the tool module's `AndroidManifest.xml` (merged into the app manifest at build time):

```xml
<service
    android:name=".service.PhoneAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

## MCP Tools

### Screen Reading

| Tool | Description | Returns |
|------|-------------|---------|
| `phone_get_screen` | Get the accessibility tree of the current screen | JSON: package, activity, flat node list |
| `phone_screenshot` | Take a screenshot (API 30+) | Base64-encoded PNG (scaled to max 720px wide) |

### Interaction

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `phone_tap` | Tap at coordinates | `x`, `y` |
| `phone_long_press` | Long press at coordinates | `x`, `y` |
| `phone_swipe` | Swipe between two points | `startX`, `startY`, `endX`, `endY`, `durationMs` |
| `phone_type_text` | Type text into the focused field | `text` |
| `phone_press_key` | Press a system key | `key` (back, home, recents, notifications, enter, delete) |
| `phone_scroll` | Scroll a scrollable element | `direction` (up/down/left/right), optional `index` (node index from `phone_get_screen` output) |

### Utility

| Tool | Description | Returns |
|------|-------------|---------|
| `phone_get_screen_info` | Get screen dimensions, density, rotation | JSON with display metadata |
| `phone_find_node` | Find nodes matching text or content-description | JSON array of matching nodes with bounds |

## Accessibility Tree Serialization

`phone_get_screen` returns a flat list with depth-based hierarchy:

```json
{
  "package": "com.example.app",
  "activity": "MainActivity",
  "nodes": [
    {
      "depth": 0,
      "index": 0,
      "class": "FrameLayout",
      "bounds": [0, 0, 1080, 2400],
      "scrollable": false
    },
    {
      "depth": 1,
      "index": 1,
      "class": "TextView",
      "text": "Hello World",
      "bounds": [100, 200, 500, 260],
      "clickable": true,
      "resourceId": "com.example:id/greeting"
    }
  ]
}
```

### Node fields

- **Always included:** `depth`, `index`, `class`, `bounds` (as `[left, top, right, bottom]`)
- **Only if non-empty:** `text`, `hint`, `contentDescription`, `resourceId`
- **Only if true:** `clickable`, `scrollable`, `focused`, `checked`, `selected`, `password`
- **Only if false:** `enabled` (defaults to true, so omitted when true)

### Pruning rules

- Nodes with zero-area bounds (invisible)
- System UI nodes (status bar, navigation bar) unless specifically requested
- Nodes deeper than max depth (default 15)

## Action Implementation

### Tap / Long Press / Swipe

Use `AccessibilityService.dispatchGesture()` with `GestureDescription`:
- **Tap:** Single point, 50ms duration
- **Long press:** Single point, 1000ms duration
- **Swipe:** Path between two points, configurable duration (default 300ms)
- Result reported via `GestureResultCallback`

### Type Text

1. Find focused node via `findFocus(FOCUS_INPUT)`
2. Use `node.performAction(ACTION_SET_TEXT)` with text in bundle
3. Fallback: clipboard paste if `ACTION_SET_TEXT` unsupported

### Press Key

- Back, Home, Recents, Notifications → `performGlobalAction()`
- Enter, Delete → `performAction()` on focused node or gesture simulation

### Scroll

- Find scrollable node (specific `index` from tree output, or first scrollable on screen)
- `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD` for vertical
- `ACTION_SCROLL_LEFT` / `ACTION_SCROLL_RIGHT` (API 33+) for horizontal, swipe gesture fallback on older APIs

### Screenshot

- `AccessibilityService.takeScreenshot()` (API 30+)
- Scale bitmap to max 720px width
- Compress as PNG, return base64
- API < 30: return error message explaining unavailability

### Response Format

All action tools return:
```json
{
  "success": true,
  "message": "Tapped at (540, 1200)",
  "timestamp": 1711612800000
}
```

## Service Configuration

**accessibility_service_config.xml:**
```xml
<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews"
    android:description="@string/accessibility_service_description" />
```

## Privacy & Safety

**Disclaim text:** "This tool has full access to your screen content and can perform actions on your behalf. It can read everything visible on screen, including sensitive information like passwords, messages, and financial data."

**Service state detection:** If the accessibility service is not enabled, tools return an actionable error: "Accessibility Service is not enabled. Please enable 'Phone MCP' in Settings > Accessibility."

## Build Integration

- **Build flavor:** Both `full` and `play` (no Play Store policy issues)
- **Convention plugin:** `mcp.android.tool`
- **Namespace:** `se.premex.mcp.uiautomation`
- **settings.gradle.kts:** Add `include(":tools:uiautomation")`
- **app/build.gradle.kts:** Add `implementation(project(":tools:uiautomation"))`
- **No new third-party dependencies** — pure Android framework APIs
