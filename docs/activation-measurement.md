# Connection guide measurement

The guide uses the current tunnel connection and paired-client list before any
historical success. A returning user therefore gets reconnect help after losing
the connection. The first sensor test is opt-in. Client choice persists across
launches; Claude, Gemini CLI and Codex each have a matching setup guide.

Play builds configured with Firebase report coarse product actions through
`JourneyAnalytics`. Full/F-Droid builds and builds without Firebase are inert.
Custom event parameters are allowlisted:

| Event-scoped dimension | Values |
| --- | --- |
| `ai_client` | `claude_code`, `claude_desktop`, `gemini_cli`, `codex`, `other` |
| `setup_step` | `connect`, `pair`, `enable_sensor`, `try_command`, `ready` |
| `base_plan` | `monthly`, `yearly` |

Client choice describes the guide selection, not a verified connected agent.
No prompts, tool results, device IDs, pairing codes or credentials are attached
to these custom events. The existing Firebase SDK still supplies its standard
analytics context as described in the privacy policy.

`remote_registered` records a successful registration in this app version.
`remote_setup_step` records an observed guide state; reopening the screen may
record it again. Client selections, guide opens and copied prompts are actions,
not proof of a successful tool call.

`remote_connected`, `remote_paired`, `remote_tool_success` and
`remote_entitlement_active` are logged once per app installation when that state
is first observed. They may describe activity or purchases predating the update.
They must not be treated as new transactions or exact event dates. Restored
purchases never create synthetic purchase/revenue events. Use verified server
milestones for activation cohorts and Google Play reports for revenue/refunds.

During the final seven trial days, users without a successful tool call receive
setup help; users who have succeeded receive the subscription reminder.
The corresponding events distinguish those messages. New GA4 dimensions require
event-scoped custom definitions and do not backfill historical data.
