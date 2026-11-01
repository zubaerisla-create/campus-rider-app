# Campus Rider — JU campus ride-sharing

A browser-based rickshaw ride-sharing app for the **Jahangirnagar University (JU)** campus.
Passengers book seats and track their rickshaw live, drivers accept requests and drive the
route, and up to **three passengers** heading the same way can share one rickshaw.

It started as a Data Structures & Algorithms course project. The original Java DSA code
(graph, Dijkstra, Trie, queues, merge sort, route compatibility) still powers it, now inside
a Spring Boot backend.

| Layer     | Technology |
|-----------|------------|
| Frontend  | React 18, Vite 6, Tailwind CSS 4, React Router, Axios, Lucide icons |
| Backend   | Java 17, Spring Boot 3.4 (Web MVC, Security, WebSocket, Validation), Maven |
| Storage   | CSV files only (`data/*.csv`), no database |
| Real-time | Spring WebSocket (`/ws/tracking`): ride updates and simulated rickshaw positions |

---

## 1. Run it locally

### Requirements
- **Java 17+** and **Maven 3.6+**
- **Node.js 20+** and **npm 10+**

### Backend (Spring Boot API on http://localhost:8080)
```bash
cd backend
mvn spring-boot:run
```
On first start the backend:
- creates any missing CSV files in `../data/` from the bundled sample campus,
- converts plain-text passwords in `passengers.csv` / `drivers.csv` to **BCrypt** hashes,
- adds map coordinates (`x,y`) to an older `locations.csv`.

To build a jar and run the tests:
```bash
cd backend
mvn clean package            # compiles, runs 60 tests, builds target/campus-rider.jar
java -jar target/campus-rider.jar
```

### Frontend (React on http://localhost:5173)
```bash
cd frontend
npm install
npm run dev
```
Open **http://localhost:5173**. The Vite dev server proxies `/api` and `/ws` to the backend,
so the browser only ever talks to one origin.

Production build: `npm run build` (output in `frontend/dist/`), then `npm run preview`.

### Demo accounts (password `1234`)
| Passengers | Drivers |
|---|---|
| `tanvir`, `nila`, `rafiq` (staff), `emergency` (emergency priority) | `karim`, `mokbul`, `salam`, `rahim`, `jamal` |

Demo rickshaws start at Dairy Gate (karim), Bottola (mokbul), Shaheed Minar (salam),
Prantik Gate (rahim) and MMH Hall (jamal). All are online.

**A two-window demo:** log in as `tanvir` in one browser and as `mokbul` in a private window.
Book Bottola → Dairy Gate, accept the request as Mokbul, press **Start ride**, then switch on
**Auto** and watch both maps move. Log in as `nila` and book Rafiq-Jabbar Hall → Dairy Gate,
choosing Mokbul's rickshaw, to see a shared ride.

---

## 2. Configuration

Backend settings live in `backend/src/main/resources/application.properties`. They can be
overridden with environment variables or `--key=value` arguments.

| Setting | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | API port |
| `CAMPUSRIDER_DATA_DIR` / `campusrider.data-dir` | `../data` | CSV folder (relative to where the backend starts) |
| `campusrider.max-detour-meters` | `750` | max extra distance for any passenger on a shared ride |
| `campusrider.max-pickup-meters` | `2500` | rickshaws farther than this from the pickup are not offered |
| `campusrider.offer-timeout-seconds` | `60` | an unanswered request moves to the next driver |
| `campusrider.search-timeout-seconds` | `180` | a request with no rickshaw at all becomes REJECTED |
| `campusrider.simulation-speed-mps` | `45` | simulated rickshaw speed (faster than real, for demos) |
| `CAMPUSRIDER_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | browser origins allowed for CORS and WebSocket |
| `CAMPUSRIDER_SECURE_COOKIES` | `false` | set `true` when served over HTTPS |

Frontend: copy `frontend/.env.example` to `frontend/.env` and set `VITE_BACKEND_URL` if the
backend is not on `http://localhost:8080`.

---

## 3. Features

**Passenger**: register and log in, edit profile and password. Pick the pickup and
destination by search (Trie autocomplete) or by tapping the map. Choose 1–3 seats, see the
shortest route, distance, time and fare. Compare available rickshaws (best match / nearest /
most seats) or let the system choose. Request a normal or **emergency** ride and see the
driver, plate and phone. Track the rickshaw live, see co-passengers on shared rides, cancel
(while pending or accepted), and view ride history.

**Driver**: register with a rickshaw (plate, battery/pedal, home stand). Go online or
offline. Receive requests with a countdown, then accept or reject (a rejected request moves to
the next driver automatically). Follow the ordered stop plan for all passengers, press
**Start ride** at a pickup and **Complete ride** at a drop-off. Move one location at a time,
or turn on **automatic simulated drive**. Set the current location, your own destination and
the seats offered (0–3). View trip history.

**Shared rides**: up to 3 seats per rickshaw. A new passenger is accepted only if the
route still works for everyone: free seats, pickup distance, the best stop order, the extra
distance, and the delay to every existing passenger's arrival must all be within limits.
Shared rides cost 20% less.

**Simulated live tracking**: no GPS. Rickshaws move only along campus roads, on the
Dijkstra paths of their stop plan. Every position change is pushed over the WebSocket, so
everyone sharing a rickshaw sees the same position at the same time.

---

## 4. DSA implementations (all in Java, all used by the app)

| # | Concept | Where | Used for |
|---|---|---|---|
| 1 | **Graph** (adjacency list) + BFS | `algorithms/CampusGraph.java` | campus roads; BFS flags unreachable places at startup |
| 2 | **Dijkstra** (binary-heap PriorityQueue) | `algorithms/Dijkstra.java` | trip route, fare, pickup distance, every leg of a shared plan, simulated movement |
| 3 | **Trie** | `algorithms/LocationTrie.java` | `GET /api/locations/search` (pickup / destination autocomplete) |
| 4 | **Queue** (FIFO `ArrayDeque`) | `algorithms/RideQueue.java` | normal requests waiting for a driver |
| 5 | **Priority Queue** (min-heap) | `algorithms/RideQueue.java` | emergency requests, always dispatched first |
| 6 | **Greedy** matching | `algorithms/GreedyRickshawMatcher.java`, `matching/RideMatchingService.java` | choosing which driver gets a request; fallback after rejection |
| 7 | **Custom Merge Sort** O(N log N) | `algorithms/MergeSort.java`, `algorithms/RickshawSorter.java` | ranking available rickshaws; history and search results |
| 8 | **State machine** | `algorithms/RideStateMachine.java` | PENDING → ACCEPTED → STARTED → COMPLETED, plus REJECTED / CANCELLED |
| 9 | **ArrayList / HashMap** | `repository/*.java` | accounts by id/username, rickshaws, rides, passenger → rides |
| 10 | **Route compatibility** | `algorithms/RouteCompatibility.java` | shared-ride feasibility, stop order, detour |

Paths are relative to `backend/src/main/java/campusrider/`. Each class explains its
algorithm, where it is used, its time complexity and why it fits. See
[`docs/viva-guide.md`](docs/viva-guide.md) for viva-style explanations.

**Greedy score** (lower is better):
`pickupDistance + 1.5 × detour − 100 × [pickup on current route] + 20 × (freeSeats − seatsNeeded)`.

---

## 5. Project structure

```
campus-rider-app/
├── backend/                         Spring Boot (Maven)
│   ├── pom.xml
│   └── src/main/java/campusrider/
│       ├── algorithms/   Graph, Dijkstra, Trie, RideQueue, MergeSort, Greedy, StateMachine, RouteCompatibility
│       ├── config/       properties, storage bean + data-folder lock, WebSocket, scheduling
│       ├── controller/   REST controllers
│       ├── dto/          request / response records (bean validation)
│       ├── exception/    API exceptions + JSON error handler
│       ├── matching/     RideMatchingService
│       ├── model/        Location, Road, Rickshaw, RideRequest, accounts, RideStatus
│       ├── repository/   in-memory HashMap/ArrayList indexes, written through to CSV
│       ├── security/     Spring Security config, CSRF, login rate limit, session principal
│       ├── service/      RideService (lifecycle + dispatch), AccountService, RouteService, fares
│       ├── storage/      CsvStorage (atomic, locked) + RFC 4180 CsvCodec
│       └── tracking/     WebSocket handler, notifier, RidePlanner, TrackingService (simulation)
├── frontend/                        React + Vite + Tailwind
│   └── src/ components/ pages/ layouts/ hooks/ services/ context/ routes/ styles/ utils/
├── data/                            runtime CSV files (created on first run, not committed)
└── docs/viva-guide.md
```

---

## 6. REST API

All endpoints are under `/api`. Errors are JSON: `{status, message, fieldErrors?}`.
State-changing requests need the CSRF header (see Security).

| Method | Path | Who |
|---|---|---|
| POST | `/auth/passenger/register`, `/auth/driver/register` | public |
| POST | `/auth/login` `{role, username, password}`, `/auth/logout` | public |
| GET | `/auth/me`, `/auth/csrf` | public |
| GET | `/locations`, `/locations/search?q=`, `/map`, `/routes/shortest-path?from=&to=` | public |
| GET / PUT | `/passengers/profile` | passenger |
| GET | `/passengers/rides`, `/passengers/rides/current` | passenger |
| GET | `/rides/available?pickup=&destination=&seats=&sort=best\|nearest\|seats` | passenger |
| POST | `/rides/request` `{pickupId, destinationId, seats, emergency, preferredRickshawId?}` | passenger |
| POST | `/rides/{id}/cancel` | passenger (own ride) |
| POST | `/rides/{id}/accept`, `/reject`, `/start`, `/complete` | driver (offered / assigned ride) |
| GET | `/rides/{id}` | the ride's passenger or driver |
| GET / PUT | `/drivers/profile` | driver |
| GET | `/drivers/dashboard`, `/drivers/requests`, `/drivers/rides` | driver |
| PUT | `/drivers/availability` `{online}`, `/drivers/rickshaw` `{currentLocationId, targetDestinationId, availableSeats}` | driver |
| POST / PUT | `/drivers/move`, `/drivers/auto-drive` `{enabled}` | driver |
| GET | `/tracking/rickshaws` | signed in |

**WebSocket** `ws://<host>/ws/tracking` (signed in; allowed origins only) pushes
`ride_update`, `driver_update` and `positions` events.

---

## 7. CSV storage (`data/`)

| File | Columns |
|---|---|
| `locations.csv` | `id,name,category,rickshawStand,aliases,x,y` |
| `roads.csv` | `from,to,distanceMeters,roadName` |
| `passengers.csv` | `id,username,passwordHash,fullName,passengerType,phone` |
| `drivers.csv` | `id,username,passwordHash,driverName,rickshawId,phone` |
| `rickshaws.csv` | `id,driverName,locationId,status,targetDestinationId,online,blockedSeats,plateNumber,vehicleType` |
| `rides.csv` | `id,passengerId,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId,seats,emergency,offeredRickshawId,preferredRickshawId,offeredAt,detourMeters,fare,shared,requestedAt,updatedAt,rejectedBy,statusNote` |
| `ride_passengers.csv` | `rideId,passengerId,passengerName,rickshawId,pickupId,destinationId,seatsBooked,detourMeters,status` |

- Columns are read **by header name**, so files from the old desktop version still load.
- Values are quoted per RFC 4180. Writes are atomic (temp file, fsync, rename).
- All access is synchronized, and a lock file stops two servers from sharing one data folder.
- Bad or duplicate rows are skipped and the original file is backed up as `<name>.bak`.
- CSV files are never served to the browser.

`data/` is git-ignored: it holds live accounts and rides. The seed data is in
`backend/src/main/resources/sample/`, with demo passwords already stored as BCrypt hashes.

---

## 8. Security

- Passwords are hashed with **BCrypt**; hashes are never returned by the API.
- Sessions use an **HttpOnly, SameSite=Lax** cookie (`CR_SESSION`). The session id is
  rotated on login.
- **CSRF**: the `XSRF-TOKEN` cookie must be echoed as the `X-XSRF-TOKEN` header (Axios does
  this automatically).
- **Role-based access**: passenger-only and driver-only URL groups. Services also check
  ownership: a passenger only sees their own rides, and a driver only sees rides offered or
  assigned to their rickshaw.
- After 5 failed logins an account is locked for 60 s. All input is validated on the
  backend, and responses never include stack traces.

---

## 9. Tests

```bash
cd backend && mvn test
```
60 tests cover:
- the DSA classes (graph, Dijkstra, Trie, queues, merge sort, greedy, state machine, route compatibility);
- CSV storage (legacy files, malformed rows, quoting, folder lock);
- auth and security over HTTP (BCrypt, CSRF, roles, session rotation, rate limit, validation);
- full ride flows (request → accept → start → move → complete, reject fallback to `REJECTED`, 3-seat sharing, concurrent booking without overbooking, emergency priority, cancellation rules, offer timeout, restart persistence, auto drive);
- WebSocket events (ride updates, shared live positions, anonymous / foreign-origin refusal).

The integration tests start the real app on a random port with a temporary data folder.

---

## 10. Troubleshooting

| Problem | Fix |
|---|---|
| `Port 8080 / 5173 already in use` | Stop the other process, or set `PORT=8081` for the backend and `VITE_BACKEND_URL=http://localhost:8081` for the frontend. |
| `Another Campus Rider server is already using .../data` | Only one backend may use a data folder at a time. Stop the other one or set `CAMPUSRIDER_DATA_DIR`. |
| Frontend says "Cannot reach the Campus Rider server" | Start the backend first. Check `curl http://localhost:8080/api/health`. |
| `403` on every POST | The CSRF cookie is missing: open the app through the Vite URL (not a different port), then reload. |
| "Offline" badge in the app bar | The WebSocket is reconnecting. Check the backend is running and the page origin is in `CAMPUSRIDER_ALLOWED_ORIGINS`. |
| JVM crashes with `SIGSEGV` during `mvn` | This came from an unstable local JVM, not from the project. `export MAVEN_OPTS="-Xshare:off -XX:+UseSerialGC"` and retry. |
| Start from a clean campus | Stop the backend and delete `data/`. It is recreated from the samples on the next start. |
