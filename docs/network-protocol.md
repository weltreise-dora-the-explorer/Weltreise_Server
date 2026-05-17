# Weltreise — Network Protocol

This document describes the wire protocol between the Android client
(`Weltreise_App`) and the Spring Boot backend (`Weltreise_Server`).

Audience: team members and future contributors working on either side of
the connection. The protocol is the source of truth — if the docs and the
code disagree, the code wins. The references in this document point at the
actual source files so the docs can be re-verified.

---

## 1. Overview

| Aspect | Value |
|---|---|
| Transport | WebSocket (RFC 6455), wrapped with the STOMP 1.2 sub-protocol |
| Server framework | Spring `@EnableWebSocketMessageBroker` simple in-memory broker |
| Client library | [Krossbow](https://github.com/joffrey-bion/krossbow) on top of OkHttp (`MyStomp.kt`) |
| Endpoint path | `/websocket-example-broker` |
| Allowed origins | `*` (CORS open — see `WebSocketBrokerConfig`) |
| Application prefix | `/app/**` — destinations the **client SENDs to** |
| Broker prefix | `/topic/**` — destinations the **client SUBSCRIBEs to** |
| Payload format | JSON (Jackson 3 — `tools.jackson.*`) |

### Endpoint URL

The full URL is built from `<scheme>://<host>:<port>/websocket-example-broker`:

| Environment | URL |
|---|---|
| Local server, Android emulator | `ws://10.0.2.2:8080/websocket-example-broker` |
| Local server, physical device | `ws://<dev-machine-LAN-ip>:8080/websocket-example-broker` |
| Uni deployment (Docker on `se2-demo.aau.at`) | `ws://se2-demo.aau.at:53205/websocket-example-broker` |

The default constant used by the client is in `MyStomp.kt`:

```kotlin
private const val WEBSOCKET_URI = "ws://10.0.2.2:8080/websocket-example-broker"
```

`10.0.2.2` is the Android-emulator alias for the host machine's loopback.

### Subscribe vs. SEND

STOMP separates inbound and outbound traffic:

* **SEND** (`client → server`) targets `/app/**` destinations. These are
  routed to `@MessageMapping` handlers in `WebSocketBrokerController`.
* **SUBSCRIBE** (`server → client`) is done against `/topic/**`
  destinations. The broker pushes every message published to a topic to
  every active subscriber.

Concretely: a player presses "Roll dice" → the app SENDs a `ROLL_DICE`
ClientCommand to `/app/lobby/{lobbyId}/command` → the controller updates
state → the controller's return value is broadcast on
`/topic/lobby/{lobbyId}/events`, where **every** subscribed player
(including the sender) receives the new state.

---

## 2. Connection Lifecycle

```
client                                                       server
  │                                                            │
  │  WebSocket upgrade (HTTP → WS)                             │
  ├───────────────────────────────────────────────────────────►│
  │                                                            │
  │  STOMP  CONNECT                                            │
  ├───────────────────────────────────────────────────────────►│
  │  STOMP  CONNECTED                                          │
  │◄───────────────────────────────────────────────────────────┤
  │                                                            │
  │  SUBSCRIBE  /topic/lobby/{lobbyId}/events                  │
  ├───────────────────────────────────────────────────────────►│
  │  SUBSCRIBE  /topic/goal-reached                            │
  ├───────────────────────────────────────────────────────────►│
  │  SUBSCRIBE  /topic/game-over                               │
  ├───────────────────────────────────────────────────────────►│
  │                                                            │
  │  SEND  /app/lobby/{lobbyId}/command  (JOIN_LOBBY …)        │
  ├───────────────────────────────────────────────────────────►│
  │  MESSAGE  /topic/lobby/{lobbyId}/events  (CommandResponse) │
  │◄───────────────────────────────────────────────────────────┤
  │                                                            │
  │  …                                                         │
  │                                                            │
  │  DISCONNECT (intentional)  or  WebSocket close (crash)     │
  ├╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌►│
  │                                                            │
  │                       (server starts 60s grace timer       │
  │                        if the session was registered to    │
  │                        a lobby — see §9 Disconnect Flow)   │
```

1. **WebSocket upgrade.** The HTTP `Upgrade: websocket` handshake.
2. **STOMP CONNECT / CONNECTED.** Sub-protocol handshake. Performed by
   Krossbow on the client side and Spring on the server side.
3. **SUBSCRIBE.** The app subscribes to the topics it expects messages on.
   The current app subscribes to `/topic/lobby/{lobbyId}/events`,
   `/topic/goal-reached`, `/topic/game-over`, plus two echo/debug topics
   (`/topic/hello-response`, `/topic/rcv-object`).
4. **SEND.** Commands flow through `/app/lobby/{lobbyId}/command`.
5. **DISCONNECT.** Triggered explicitly by the client, or implicitly when
   the WebSocket closes (server stop, network loss, app killed).

---

## 3. Identifiers

Three IDs flow through the protocol. They are used together; none of them
alone fully identifies a user.

| ID | Type | Origin | Purpose | Persistence |
|---|---|---|---|---|
| `playerId` | string | chosen by the user | Display name & per-lobby identity. Compared by string equality, so trailing whitespace matters. | per session in `LobbyService` / `InMemoryLobbyStore` |
| `clientId` | UUID string | generated once per install, stored in `SharedPreferences` (`PreferencesHelper`) | Lets the server tell two devices apart even if both pick the same `playerId`, and authorizes a `REJOIN_LOBBY`. | server: stored on `PlayerState.clientId`; client: `SharedPreferences` |
| `lobbyId` | string (4-digit PIN in the UI) | chosen on `CREATE_LOBBY` | Routing key for SEND and SUBSCRIBE paths. | server: key of `InMemoryLobbyStore.lobbies`, persisted to `lobbies.json` |

Example values used throughout this document:

```
lobbyId   = "1234"
playerId  = "Marco"
clientId  = "aaa-111-bbb-222"
```

Mismatch rule for rejoin: if the player already has a `clientId` recorded
and the rejoin request brings a different one, the server rejects it
with `PLAYER_NOT_IN_LOBBY` (`LobbyService.rejoinLobby`).

---

## 4. Topics (Server → Client)

All payloads are JSON. Java DTOs are referenced where they exist.

### 4.1 `/topic/lobby/{lobbyId}/events`

Defined in `WebSocketTopics.lobbyEvents(String lobbyId)`. The main fan-out
channel for everything that happens inside a single lobby. The payload is
always a `CommandResponse` (see §6).

The server publishes here in two situations:

* As the return value of any `@MessageMapping("/lobby/{lobbyId}/command")`
  invocation (`@SendTo("/topic/lobby/{lobbyId}/events")` in
  `WebSocketBrokerController.handleLobbyCommand`).
* As an explicit `messagingTemplate.convertAndSend(...)` call from
  `StompDisconnectListener` when a player disconnects or their grace
  period expires.

Every connected player in the lobby receives every message on this topic.

### 4.2 `/topic/goal-reached`

Constant: `WebSocketTopics.GOAL_REACHED = "/topic/goal-reached"`. Pushed
by `GameCommandService.broadcastGoalReached` when a player visits one of
their assigned cities.

```json
{
  "playerName": "Marco",
  "cityName": "Quito",
  "reached": 1,
  "total": 5
}
```

Schema: `GoalReachedMessage` (`playerName`, `cityName`, `reached`, `total`).

### 4.3 `/topic/game-over`

Constant: `WebSocketTopics.GAME_OVER = "/topic/game-over"`. Pushed by
`GameCommandService.broadcastGameOver` when the last outstanding goal is
reached. The payload is a `GameOverMessage` containing per-player scores
(`PlayerScore`):

```json
{
  "scores": [
    { "playerName": "Marco", "score": 5 },
    { "playerName": "Lena",  "score": 3 }
  ]
}
```

Score is computed by `GameCommandService.calculateScore` as
`visitedCities.size() − (ownedCities.size() − visitedCities.size())`.

### 4.4 `/topic/hello-response` *(debug)*

Echo channel — receives every payload sent to `/app/hello`, prefixed
with `"echo from broker: "`. Used only for connectivity smoke tests.

### 4.5 `/topic/rcv-object` *(debug)*

Echo channel for `StompMessage` (`from`, `text`). Receives every payload
sent to `/app/object` unchanged. Used for serialisation smoke tests.

### 4.6 `/actuator/health` *(HTTP, not STOMP)*

Plain HTTP endpoint provided by Spring Boot Actuator. See §10.

---

## 5. Endpoints (Client → Server)

All payloads are JSON, mapped onto Java DTOs via Jackson.

### 5.1 `/app/lobby/{lobbyId}/command`

`@MessageMapping("/lobby/{lobbyId}/command")` in
`WebSocketBrokerController.handleLobbyCommand`. The only endpoint that
matters for gameplay. Accepts a `ClientCommand` payload (see §6) and
returns a `CommandResponse` which Spring forwards to
`/topic/lobby/{lobbyId}/events`.

The `{lobbyId}` path variable always replaces `command.lobbyId` on the
server (see `WebSocketBrokerController` line 66 —
`command.setLobbyId(lobbyId);`). The client can therefore omit it from
the payload; the path is authoritative.

Accepts every `CommandType` value except the three server-only ones
(`LOBBY_CLOSED`, `PLAYER_DISCONNECTED`, `PLAYER_RECONNECTED`).

### 5.2 `/app/hello` *(debug)*

`@MessageMapping("/hello")`. Returns `"echo from broker: " + text` on
`/topic/hello-response`.

### 5.3 `/app/object` *(debug)*

`@MessageMapping("/object")`. Accepts a `StompMessage` and echoes it back
on `/topic/rcv-object`.

---

## 6. Command Types

`CommandType` enum (`messaging/dtos/CommandType.java`) has 14 values.
They are split into two groups:

* **Client requests** — sent via `/app/lobby/{lobbyId}/command`.
* **Server-only broadcasts** — never appear in an inbound `ClientCommand`,
  only in the `commandType` field of an outbound `CommandResponse`.

### Request payload — `ClientCommand`

```jsonc
{
  "type":         "JOIN_LOBBY",       // CommandType, required
  "lobbyId":      "1234",             // optional — server overrides from URL
  "playerId":     "Marco",            // required for almost every command
  "moveSteps":    null,               // for MOVE_TOKEN, currently unused
  "stops":        null,               // for START_GAME — optional, default 12
  "targetCityId": null,               // for MOVE_TO_CITY
  "gameMode":     null,               // for UPDATE_GAME_MODE — enum value
  "clientId":     "aaa-111-bbb-222"   // for CREATE_LOBBY / JOIN_LOBBY / REJOIN_LOBBY
}
```

All fields except `type` are optional at the DTO level — required-ness
depends on the command (see per-command tables below). Unknown fields are
ignored by Jackson; missing fields deserialise to `null`/0.

### Response payload — `CommandResponse`

```jsonc
{
  "success":     true,                // false on every error path
  "message":     "OK",                // human-readable
  "errorCode":   null,                // ErrorCode enum on failure, else null
  "lobbyId":     "1234",
  "commandType": "JOIN_LOBBY",        // type that produced this response
  "state":       { /* GameRoomState — see §8 */ }
}
```

On failure the controller returns
`new CommandResponse(false, ex.getMessage(), ex.getErrorCode(), lobbyId, commandType, null)`.

### 6.1 Command reference

| Command | Direction | Who may send | Required fields | Triggers (response `commandType`) |
|---|---|---|---|---|
| `CREATE_LOBBY` | client → server | anyone | `playerId`, `clientId` | `CREATE_LOBBY` |
| `JOIN_LOBBY` | client → server | anyone (not yet in lobby) | `playerId`, `clientId` | `JOIN_LOBBY` |
| `LEAVE_LOBBY` | client → server | any player in the lobby | `playerId` | `LEAVE_LOBBY` (or `LOBBY_CLOSED` if host left) |
| `LOBBY_CLOSED` | server → client | — | — | broadcast only |
| `START_GAME` | client → server | host (validated by game state) | `playerId`, `stops` (opt., else from gameMode) | `START_GAME` |
| `UPDATE_GAME_MODE` | client → server | host (UI gate) | `playerId`, `gameMode` | `UPDATE_GAME_MODE` |
| `ROLL_DICE` | client → server | current turn player | `playerId` | `ROLL_DICE` |
| `MOVE_TOKEN` | client → server | current turn player | `playerId`, `moveSteps` | `MOVE_TOKEN` |
| `MOVE_TO_CITY` | client → server | current turn player | `playerId`, `targetCityId` | `MOVE_TO_CITY` |
| `END_TURN` | client → server | current turn player | `playerId` | `END_TURN` |
| `RESET_LOBBY` | client → server | host (validated server-side) | `playerId` | `RESET_LOBBY` |
| `REJOIN_LOBBY` | client → server | a player currently marked disconnected | `playerId`, `clientId` | `PLAYER_RECONNECTED` |
| `PLAYER_DISCONNECTED` | server → client | — | — | broadcast only |
| `PLAYER_RECONNECTED` | server → client | — | — | broadcast only (also the `commandType` returned to the rejoining client) |

### 6.2 Payload examples

The state objects are abbreviated for readability — see §8 for the full
`GameRoomState` schema. `commandType` in the response is the value the
server places on the wire; it is **not** always equal to the request type
(notably `LEAVE_LOBBY` may produce `LOBBY_CLOSED`, and `REJOIN_LOBBY`
produces `PLAYER_RECONNECTED`).

#### CREATE_LOBBY

```json
// request
{
  "type": "CREATE_LOBBY",
  "playerId": "Marco",
  "clientId": "aaa-111-bbb-222"
}
```
```json
// response on /topic/lobby/1234/events
{
  "success": true,
  "message": "OK",
  "errorCode": null,
  "lobbyId": "1234",
  "commandType": "CREATE_LOBBY",
  "state": {
    "lobbyId": "1234",
    "hostId": "Marco",
    "players": [
      { "playerId": "Marco", "clientId": "aaa-111-bbb-222", "connected": true,
        "boardPosition": 0, "remainingSteps": 0,
        "ownedCities": [], "visitedCities": [] }
    ],
    "phase": "LOBBY",
    "gameMode": "CITY_HOPPER",
    "gameOver": false,
    "version": 0,
    "validMoveIds": []
  }
}
```

Errors: `GAME_ALREADY_STARTED` (lobby with this id already exists),
`MISSING_PLAYER_ID`.

#### JOIN_LOBBY

```json
// request
{ "type": "JOIN_LOBBY", "playerId": "Lena", "clientId": "bbb-222-ccc-333" }
```

The response is structurally identical to `CREATE_LOBBY` but `players`
now contains two entries and `commandType` is `JOIN_LOBBY`. Errors:
`LOBBY_NOT_FOUND`, `CANNOT_JOIN_STARTED_GAME`, `LOBBY_FULL` (max 4 — see
`LobbyService.MAX_PLAYERS_IN_LOBBY`), `PLAYER_ALREADY_JOINED`,
`MISSING_PLAYER_ID`.

#### LEAVE_LOBBY

```json
// request
{ "type": "LEAVE_LOBBY", "playerId": "Lena" }
```

If a non-host leaves the response carries `"commandType": "LEAVE_LOBBY"`
and a `state` with that player removed. If the host leaves (or the last
remaining player), `lobbyClosed` is true and the controller switches the
response to `"commandType": "LOBBY_CLOSED"` — clients use that to leave
the screen. Errors: `LOBBY_NOT_FOUND`, `PLAYER_NOT_IN_LOBBY`,
`MISSING_PLAYER_ID`.

#### START_GAME

```json
// request
{ "type": "START_GAME", "playerId": "Marco", "stops": 9 }
```

If `stops` is missing the controller defaults to `12`, but if the lobby
has a `gameMode` set the controller uses `gameMode.getStops()` instead
(`WebSocketBrokerController` line 108). The response state has
`"phase": "IN_TURN"`, each `players[i]` has its `ownedCities`,
`startCity`, `currentCity` filled, and `currentPlayerId` is set to the
first player. Errors: `LOBBY_NOT_FOUND`, `GAME_ALREADY_STARTED`,
`MIN_PLAYERS_NOT_REACHED` (`< 2`).

#### UPDATE_GAME_MODE

```json
// request
{ "type": "UPDATE_GAME_MODE", "playerId": "Marco", "gameMode": "GRAND_TOUR" }
```

Updates `state.gameMode`. Validated in `GameCommandService`. Errors:
`LOBBY_NOT_FOUND`, `INVALID_PHASE` (only allowed while `phase == LOBBY`).

#### ROLL_DICE

```json
// request
{ "type": "ROLL_DICE", "playerId": "Marco" }
```

Response state contains the new `lastDiceValue` (1-6) and an updated
`validMoveIds` (which cities the current player may move to). Errors:
`NOT_YOUR_TURN`, `DICE_ALREADY_ROLLED`, `INVALID_PHASE`, `GAME_OVER`.

#### MOVE_TOKEN

```json
// request
{ "type": "MOVE_TOKEN", "playerId": "Marco", "moveSteps": 3 }
```

Decrements `remainingSteps`, updates `boardPosition`. Errors:
`ROLL_REQUIRED_BEFORE_MOVE`, `NOT_YOUR_TURN`, `MISSING_MOVE_STEPS`,
`INVALID_MOVE_STEPS`.

#### MOVE_TO_CITY

```json
// request
{ "type": "MOVE_TO_CITY", "playerId": "Marco", "targetCityId": "quito" }
```

Moves the player to the named city; the city must appear in
`state.validMoveIds`. If `quito` is also in the player's `ownedCities` it
is added to `visitedCities`, a `GoalReachedMessage` is broadcast on
`/topic/goal-reached`, and if the player has now reached all their goals
the game may end (broadcast on `/topic/game-over`). Errors:
`CITY_NOT_FOUND`, `INVALID_MOVE_TARGET`, `NOT_YOUR_TURN`, `GAME_OVER`.

#### END_TURN

```json
// request
{ "type": "END_TURN", "playerId": "Marco" }
```

Hands control to the next player. Errors: `NOT_YOUR_TURN`,
`CURRENT_PLAYER_NOT_SET`.

#### RESET_LOBBY

```json
// request
{ "type": "RESET_LOBBY", "playerId": "Marco" }
```

Host-only (server checks `playerId.equals(state.hostId)` —
`LobbyService.resetLobby` line 235; non-host triggers
`MISSING_PLAYER_ID`). Resets phase to `LOBBY`, clears player
positions and city assignments. Errors: `LOBBY_NOT_FOUND`,
`MISSING_PLAYER_ID` (non-host attempt).

#### REJOIN_LOBBY

```json
// request
{ "type": "REJOIN_LOBBY", "playerId": "Lena", "clientId": "bbb-222-ccc-333" }
```
```json
// response — note the synthetic commandType
{
  "success": true,
  "message": "OK",
  "errorCode": null,
  "lobbyId": "1234",
  "commandType": "PLAYER_RECONNECTED",
  "state": { /* current state with players[i].connected flipped back to true */ }
}
```

The controller calls `disconnectScheduler.cancel(lobbyId, playerId)`
so the 60-second timer is aborted. Errors: `LOBBY_NOT_FOUND`,
`PLAYER_NOT_IN_LOBBY` (also raised on `clientId` mismatch).

#### PLAYER_DISCONNECTED *(server → client, not a request)*

Broadcast by `StompDisconnectListener.handleDisconnect` when a STOMP
session that was registered to a lobby disappears. Same envelope as any
other event:

```json
{
  "success": true,
  "message": "OK",
  "errorCode": null,
  "lobbyId": "1234",
  "commandType": "PLAYER_DISCONNECTED",
  "state": { /* state with players[i].connected = false for the gone player */ }
}
```

#### PLAYER_RECONNECTED *(server → client, not a request)*

Same envelope, produced as the response to a `REJOIN_LOBBY`. See above.

#### LOBBY_CLOSED *(server → client, not a request)*

Sent in two places:

* As the `commandType` of a `LEAVE_LOBBY` response when
  `LobbyLeaveResult.lobbyClosed()` is true (host left or last player
  left).
* As the broadcast type from `StompDisconnectListener.handleGracePeriodExpired`
  when removing the last remaining player also tore down the lobby.

The `state` field is still populated for clients that want to render a
goodbye screen with the final roster.

---

## 7. Error Codes

`messaging/dtos/ErrorCode.java` defines 22 enum values. The
`CommandResponse` carries the failing code in `errorCode` and a
human-readable explanation in `message`. `success` is always `false` on
error and `state` is always `null`.

| Code | When |
|---|---|
| `INVALID_COMMAND` | Generic fallback for malformed commands. |
| `MISSING_COMMAND_TYPE` | Inbound payload had no `type` field. |
| `MISSING_PLAYER_ID` | `playerId` empty/blank, or non-host attempted a host-only command. |
| `LOBBY_NOT_FOUND` | `lobbyId` from the path has no matching state in `InMemoryLobbyStore`. |
| `GAME_ALREADY_STARTED` | `CREATE_LOBBY` for an existing id, or `START_GAME` while not in `LOBBY` phase. |
| `CANNOT_JOIN_STARTED_GAME` | `JOIN_LOBBY` while the target lobby is past `LOBBY` phase. |
| `PLAYER_ALREADY_JOINED` | `JOIN_LOBBY` from a `playerId` already in the lobby. |
| `PLAYER_NOT_IN_LOBBY` | `LEAVE_LOBBY`/`REJOIN_LOBBY`/turn command for an unknown player, or `REJOIN_LOBBY` with a mismatched `clientId`. |
| `MIN_PLAYERS_NOT_REACHED` | `START_GAME` with fewer than `MIN_PLAYERS_TO_START = 2`. |
| `INVALID_PHASE` | Command not allowed in the current `GamePhase` (e.g. `UPDATE_GAME_MODE` after start). |
| `CURRENT_PLAYER_NOT_SET` | `END_TURN` / turn commands while `state.currentPlayerId == null`. |
| `NOT_YOUR_TURN` | Turn command from a player other than `currentPlayerId`. |
| `DICE_ALREADY_ROLLED` | Second `ROLL_DICE` in the same turn. |
| `ROLL_REQUIRED_BEFORE_MOVE` | `MOVE_TOKEN`/`MOVE_TO_CITY` before a `ROLL_DICE`. |
| `MISSING_MOVE_STEPS` | `MOVE_TOKEN` without `moveSteps`. |
| `INVALID_MOVE_STEPS` | `moveSteps` ≤ 0 or > remaining steps. |
| `UNSUPPORTED_COMMAND_TYPE` | Server-only types (`LOBBY_CLOSED`, `PLAYER_DISCONNECTED`, …) sent from a client. |
| `INTERNAL_ERROR` | Catch-all for unexpected runtime exceptions in the controller. |
| `LOBBY_FULL` | `JOIN_LOBBY` with already `MAX_PLAYERS_IN_LOBBY = 4` players. |
| `CITY_NOT_FOUND` | `MOVE_TO_CITY` with an unknown `targetCityId`. |
| `INVALID_MOVE_TARGET` | `targetCityId` not in current `validMoveIds`. |
| `GAME_OVER` | Any turn command after the game has ended. |

### Example failure response

```json
{
  "success": false,
  "message": "Lobby is full",
  "errorCode": "LOBBY_FULL",
  "lobbyId": "1234",
  "commandType": "JOIN_LOBBY",
  "state": null
}
```

---

## 8. Game State Schema

### 8.1 `GameRoomState`

Source of truth: `messaging/dtos/GameRoomState.java`. The complete payload
that lives at the root of every successful `CommandResponse.state`:

```jsonc
{
  "lobbyId":         "1234",                    // string, the PIN
  "hostId":          "Marco",                   // playerId of the creator
  "players":         [ /* PlayerState */ ],     // 0..MAX_PLAYERS_IN_LOBBY (4)
  "phase":           "LOBBY",                   // GamePhase: LOBBY | CITY_ASSIGNMENT | IN_TURN
  "currentPlayerId": "Marco",                   // null while in LOBBY phase
  "lastDiceValue":   null,                      // null or 1..6
  "version":         0,                         // monotonically increasing on each mutation
  "validMoveIds":    [ "quito", "dakar" ],      // city ids the current player may move to
  "gameMode":        "CITY_HOPPER",             // GameMode (see §8.3)
  "gameOver":        false                      // true after the last goal is reached
}
```

The `version` counter is bumped by every `LobbyService` and
`GameCommandService` method that mutates state and is intended for clients
that want to ignore stale snapshots.

### 8.2 `GamePhase`

`messaging/dtos/GamePhase.java`:

| Value | Meaning |
|---|---|
| `LOBBY` | Players are joining/leaving, host can update `gameMode`. |
| `CITY_ASSIGNMENT` | Defined in the enum but currently unused in the live flow — `START_GAME` jumps straight to `IN_TURN`. |
| `IN_TURN` | Game is running. `ROLL_DICE`, `MOVE_TO_CITY`, `END_TURN` are valid. |

### 8.3 `GameMode`

`messaging/dtos/GameMode.java`:

| Value | `displayName` | `stops` |
|---|---|---|
| `CITY_HOPPER` | `City Hopper` | 6 |
| `GRAND_TOUR` | `Grand Tour` | 9 |
| `EPIC_VOYAGE` | `Epic Voyage` | 12 |

`stops` controls how many target cities the start-of-game distribution
hands to each player.

### 8.4 `PlayerState`

`game/models/PlayerState.java`. Embedded in `GameRoomState.players`.

```jsonc
{
  "playerId":         "Marco",                  // string, display name + per-lobby identity
  "clientId":         "aaa-111-bbb-222",        // UUID; null if the client never sent one
  "connected":        true,                     // false during a grace period
  "startCity":        { /* City */ },           // assigned by START_GAME, then immutable
  "currentCity":      { /* City */ },           // mutated by MOVE_TO_CITY
  "boardPosition":    0,                        // step counter; mutated by MOVE_TOKEN
  "remainingSteps":   0,                        // set by ROLL_DICE, decremented by MOVE_TOKEN
  "previousCityId":   null,                     // used to prevent immediate back-and-forth
  "ownedCities":      [ /* City, City, ... */ ],// the goal list this player must visit
  "visitedCities":    [ /* City, ... */ ],      // subset of ownedCities already reached
  "allTargetsReached":false,                    // derived: ownedCities.size() == visitedCities.size()
  "progressStatus":   "0 / 5"                   // derived: "<visited>/<owned>"
}
```

`allTargetsReached` and `progressStatus` are computed getters
(`isAllTargetsReached()` / `getProgressStatus()`) and therefore appear in
the serialized JSON via Jackson's bean-property discovery.

### 8.5 `City`

`game/models/City.java`:

```jsonc
{
  "id":        "quito",
  "name":      "Quito",
  "continent": "AMERICAS_OCEANIA",   // Continent: EUROPE_AFRICA | ASIA | AMERICAS_OCEANIA
  "color":     "RED"                 // CityColor: ORANGE | RED | GREEN
}
```

---

## 9. Notable Flows

### 9.1 Create lobby → join → start game

```
Host                    Server                   Guest
  │                        │                       │
  │ CREATE_LOBBY 1234      │                       │
  ├───────────────────────►│                       │
  │ MESSAGE  (state v0)    │ MESSAGE  (state v0)   │
  │◄───────────────────────┼──────────────────────►│
  │                        │                       │
  │                        │ JOIN_LOBBY 1234       │
  │                        │◄──────────────────────┤
  │ MESSAGE  (state v1)    │ MESSAGE  (state v1)   │
  │◄───────────────────────┼──────────────────────►│
  │                        │                       │
  │ START_GAME             │                       │
  ├───────────────────────►│                       │
  │ MESSAGE  (phase=       │ MESSAGE  (phase=      │
  │ IN_TURN, cities        │ IN_TURN, cities       │
  │ assigned, v=2)         │ assigned, v=2)        │
  │◄───────────────────────┼──────────────────────►│
```

Each `MESSAGE` is a single envelope broadcast to **both** subscribers of
`/topic/lobby/1234/events`. Clients render the new state idempotently.

### 9.2 Dice roll → city move

The current-player check fails fast if anyone else sends these. `validMoveIds`
in the response after `ROLL_DICE` tells the client which UI elements to
enable.

```
Active player         Server                  Everyone
  │                     │                       │
  │ ROLL_DICE           │                       │
  ├────────────────────►│                       │
  │ MESSAGE  (lastDice= │ MESSAGE  (lastDice=   │
  │ 4, validMoveIds=…)  │ 4, validMoveIds=…)    │
  │◄────────────────────┼──────────────────────►│
  │                     │                       │
  │ MOVE_TO_CITY "quito"│                       │
  ├────────────────────►│                       │
  │ MESSAGE  (currentCity=quito, …)             │
  │◄────────────────────┼──────────────────────►│
  │                     │                       │
  │                     │   (if goal reached:)  │
  │                     ├──/topic/goal-reached──►
  │                     │                       │
  │ END_TURN            │                       │
  ├────────────────────►│                       │
  │ MESSAGE  (currentPlayerId=next player)      │
  │◄────────────────────┼──────────────────────►│
```

### 9.3 Player leaves

Two paths through `LobbyService.leaveLobby`:

* **Host leaves** → all players are cleared, the lobby is removed from
  the store, response goes out as `"commandType": "LOBBY_CLOSED"`.
* **Non-host leaves** → only that player is removed. If they were the
  `currentPlayerId` the next player is selected. If exactly one player
  remains *and* the game was past `LOBBY`, the state is reset to lobby
  phase (`resetGameToLobbyPhase`) so the remaining player can wait for
  others. Response goes out as `"commandType": "LEAVE_LOBBY"`.

### 9.4 Disconnect → grace period → reconnect

`DisconnectScheduler.GRACE_PERIOD_SECONDS = 60`. Server-side timeline:

```
   T+0                  T<60s                       T+60s
    │                      │                          │
    │ session closes       │ REJOIN_LOBBY received    │ no rejoin?
    ▼                      ▼                          ▼
StompDisconnect         REJOIN_LOBBY path         scheduler fires
Listener fires          in controller             handleGracePeriodExpired
    │                      │                          │
    ├ mark connected=false ├ cancel timer             ├ removeDisconnectedPlayer
    ├ broadcast            ├ flip connected=true      │  (= leaveLobby)
    │   PLAYER_DISCONNECTED├ broadcast                ├ broadcast
    └ schedule 60s timer   │   PLAYER_RECONNECTED     │   LEAVE_LOBBY or
                                                      │   LOBBY_CLOSED
```

Concretely:

1. The WebSocket session closes (server stop, app killed, network drop).
   Spring fires `SessionDisconnectEvent`, picked up by
   `StompDisconnectListener.handleDisconnect`.
2. The listener calls `lobbyService.markPlayerDisconnected(...)` — the
   player keeps their slot but `connected` flips to `false`. The new
   state is broadcast on `/topic/lobby/{lobbyId}/events` with
   `commandType = PLAYER_DISCONNECTED`. Other clients can render an
   "is reconnecting…" indicator.
3. The listener registers a 60-second timer with `DisconnectScheduler`.
4. If `REJOIN_LOBBY` arrives in time the timer is cancelled and the
   `connected` flag flips back; the broadcast type is `PLAYER_RECONNECTED`.
5. If the timer fires first, `handleGracePeriodExpired` calls
   `lobbyService.removeDisconnectedPlayer(...)` (a tolerant wrapper around
   `leaveLobby`) and broadcasts `LEAVE_LOBBY` or `LOBBY_CLOSED` depending
   on whether the lobby still has players.

### 9.5 Auto-reset on solo survivor

`LobbyService.leaveLobby` (lines 169-171, comments preserved in code):
if a `LEAVE_LOBBY` reduces the lobby to a single player while the game
was past `LOBBY` phase, `resetGameToLobbyPhase` is called — phase is set
to `LOBBY`, city/position state is wiped, `gameOver = false`, players are
preserved. The leftover player can wait for new joiners and start a new
game without re-creating the lobby.

### 9.6 Container restart recovery (client-visible flow)

The server-side persistence layer (`LobbyPersistence` /
`InMemoryLobbyStore`) means a container restart **does not** lose
lobbies. From the client's perspective:

1. Server crashes / is restarted. WebSocket dies.
2. `MyStomp.handleConnectionLost` triggers
   `callbacks.onConnectionLost()` → the UI shows the reconnect overlay.
3. `MyStomp.scheduleReconnect` retries with exponential backoff
   (2s → 4s → 8s → … capped at 30s, see `RECONNECT_INITIAL_DELAY_MS` and
   `RECONNECT_MAX_DELAY_MS`).
4. When the connection comes back, `onReconnected()` reads
   `prefs.getLobbyId()` and the current `playerName`, and sends
   `REJOIN_LOBBY` with the persisted `clientId`.
5. The server, having just restored `lobbies.json` into memory in
   `InMemoryLobbyStore.loadFromPersistence`, recognises the player by
   `playerId` + `clientId` and responds with a `PLAYER_RECONNECTED`
   broadcast.
6. The app applies the restored state and the player is back in their
   game.

If no `REJOIN_LOBBY` arrives within the grace period the server tears
the player down as in §9.4.

---

## 10. Health Endpoint

Pure HTTP, served by Spring Boot Actuator.

| | |
|---|---|
| Path | `/actuator/health` |
| Method | `GET` |
| Local URL | `http://localhost:8080/actuator/health` |
| Uni URL | `http://se2-demo.aau.at:53205/actuator/health` |
| Configuration | `application.properties` — `management.endpoints.web.exposure.include=health` and `management.endpoint.health.show-details=never` |

Sample response:

```json
{ "status": "UP" }
```

`show-details=never` means no component breakdown is exposed, so the
endpoint is safe to leave open: it leaks no information about the
internals. It is used as the Docker container health-check signal and as
the target for any external uptime monitor.
