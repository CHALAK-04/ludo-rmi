# Ludo — Online Multiplayer Board Game (Java RMI)

A four-player networked Ludo game built on Java RMI, with authentication, a lobby
system, a friend system, and real-time game state synchronisation across clients.

Solo project — COURSE_NAME, Lebanese University, Faculty of Engineering (Branch 1), YEAR.
~5,600 lines of Java.

## What it does

- Register and log in with accounts that persist across server restarts
- Lobby system: create, join and leave game rooms
- Friend system with live online / offline / in-game status
- Full Ludo rules: dice rolls, token movement, captures, home paths, win detection
- All clients see moves as they happen, pushed from the server

## Architecture

Four independent RMI services, each bound separately in the registry:

| Service | Responsibility |
|---|---|
| `IAuthService` | Registration, login, session state |
| `ILobbyService` | Room creation, joining, player lists |
| `IFriendService` | Friend requests, presence updates |
| `IGameService` | Turn order, dice, moves, win conditions |

The package layout separates the remote contract from its implementation:

```
COMMON/    remote interfaces + serialisable models (User, Lobby, GameState)
SERVER/    service implementations, file-backed persistence
CLIENT/    Swing UI, observer implementations
REGISTRY/  standalone RMI registry
```

### Bidirectional RMI

The clients aren't only callers — they export remote objects of their own
(`IGameObserver`, `ILobbyObserver`) and register them with the server. When a
player moves, the server invokes a method *on every other client*.

No polling: state changes are pushed. This is the core of the design and the
reason a move appears on four screens at once.

### Concurrency and persistence

Shared state lives in `ConcurrentHashMap`, because RMI serves each incoming call
on its own thread. Accounts persist through Java serialisation, written to a
temporary file and then renamed, so a crash mid-write cannot corrupt the store.

## Running it

Requires Java 17 or later.

```bash
# Compile
javac -d out $(find src -name "*.java")

# 1. Start the registry
java -cp out Ludo.REGISTRY.Registry

# 2. Start the server (new terminal)
java -cp out Ludo.SERVER.ServerMain

# 3. Start a client (new terminal, repeat for up to 4 players)
java -cp out Ludo.CLIENT.ClientMain
```

The server and all clients must run on the same machine — see below.

## What I'd do differently

- **Passwords are stored unhashed.** Authentication was scoped to the course
  requirements; a production version would store a PBKDF2 or BCrypt hash rather
  than the password itself.
- **The host address is hardcoded** to `127.0.0.1` across the service lookups, so
  the server and clients cannot run on separate machines. This belongs in a
  config file or a launch argument.
- **UI and networking are entangled.** `GameBoard` and `LobbyForm` run to over
  1,200 lines each and call RMI directly from Swing event handlers. These belong
  behind a controller layer.
- **The server calls itself over RMI.** `GameServiceImpl` performs a
  `Naming.lookup` to reach the lobby service where a direct reference would do.
  It works, but it is a remote call for no reason.
- **No tests.** The game rules — captures, home entry, win detection — are pure
  logic and should have been unit tested.
