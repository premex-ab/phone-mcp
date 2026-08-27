# PhoneMCP remote tunnel protocol

When *Remote access* is enabled, the app keeps an outbound WebSocket open to
the PhoneMCP relay (`https://phonemcp.ai`). The relay gives the phone a public
HTTPS MCP endpoint and forwards MCP traffic over the tunnel:

```
MCP client ──https──> relay ──wss tunnel──> phone ──http 127.0.0.1──> local MCP server
```

The app side lives in
[`app/src/main/java/se/premex/mcp/remote/TunnelClient.kt`](app/src/main/java/se/premex/mcp/remote/TunnelClient.kt).

## Device API (app ⇄ relay, HTTPS)

| Call | Description |
|---|---|
| `POST /api/devices/register` `{"name": "..."}` | Returns `{"deviceId", "deviceSecret"}`. Called once, when Remote access is first enabled. |
| `POST /api/devices/{deviceId}/pairing-code` with header `X-Device-Secret` | Returns `{"code", "expiresInSeconds"}`. The user enters this code in the browser during the relay's OAuth flow to bind a client to this device. |

## Tunnel (app ⇄ relay, WebSocket at `/tunnel`)

JSON text frames. The phone opens the connection and authenticates first:

| Direction | Frame |
|---|---|
| phone → relay | `{"type":"hello","deviceId":"…","secret":"…"}` |
| relay → phone | `{"type":"hello_ok"}` — or `{"type":"error","message":"…"}` and close |

The relay then forwards each HTTP request as:

| Direction | Frame |
|---|---|
| relay → phone | `{"type":"req","id":"r1","method":"GET","path":"/sse","headers":{…},"body":"…"?}` (`body` is UTF-8 text) |
| phone → relay | `{"type":"head","id":"r1","status":200,"headers":{…}}` |
| phone → relay | `{"type":"chunk","id":"r1","data":"<base64>"}` (repeated; streams SSE as it happens) |
| phone → relay | `{"type":"end","id":"r1"}` — or `{"type":"error","id":"r1","message":"…"}` |
| relay → phone | `{"type":"cancel","id":"r1"}` when the MCP client goes away |

Multiple requests are multiplexed concurrently over one tunnel by `id`.

## Security properties

- The phone only ever makes **outbound** connections; nothing is exposed inbound.
- The relay never learns the phone's **local auth token**: the app replaces the
  `Authorization` header on forwarded requests with its own local token.
- MCP clients authenticate to the relay with OAuth 2.1 (authorization-code +
  PKCE). Authorization is proven by entering the single-use pairing code shown
  in the app; issued tokens are bound to that one device.
- Every tool remains opt-in on the phone regardless of transport.
- The relay terminates TLS and can observe forwarded MCP traffic. The relay URL
  is a constant in the app (`RemoteAccessConfig.RELAY_URL`) — the hosted relay
  at phonemcp.ai is the supported way to use Remote access.
