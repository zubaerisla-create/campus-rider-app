# Viva Preparation Guide

Short explanations of every data structure and algorithm in the project, with
the file to open if the examiner asks to see the code.

Notation: V = number of locations (25), E = number of roads (33),
R = number of rickshaws, n = number of pending requests.

---

## 1. Graph

**What it is.** A set of vertices (points) connected by edges (lines).
Here a vertex is a campus location and an edge is a road. Each road has a
weight: its length in meters. Roads are two-way, so the graph is
*undirected* and *weighted*.

**Why a graph.** Roads on a campus meet at junctions and form cycles. A list or a
tree cannot model "many ways to get from A to B"; a graph can.

File: `backend/src/main/java/campusrider/algorithms/CampusGraph.java`

## 2. Adjacency list

**What it is.** For every vertex, a list of its edges:

```
dairy_gate    -> [shaheed_minar 116.6, zahir_raihan_auditorium 123.3, ...]
shaheed_minar -> [dairy_gate 116.6, new_arts_building 95.2, ...]
```

Stored as `Map<String, List<Edge>>`. A two-way road is added twice, once in
each direction (`addRoad`).

**Why not an adjacency matrix?** A matrix needs V x V cells (625 here), most of
them empty because each place only touches a few roads. An adjacency list uses
O(V + E) memory and lets Dijkstra loop over just the real neighbours.

| Operation               | Adjacency list | Adjacency matrix |
|-------------------------|----------------|------------------|
| Memory                  | O(V + E)       | O(V^2)           |
| List neighbours of u    | O(degree(u))   | O(V)             |
| Is there a road u-v?    | O(degree(u))   | O(1)             |

Validation: unknown locations, a road to itself, and zero, negative or NaN
lengths are rejected.

## 3. Dijkstra's shortest path

File: `backend/src/main/java/campusrider/algorithms/Dijkstra.java`

**Idea.** Keep a "best known distance" for every location. Repeatedly take the
unvisited location with the smallest distance. Because all road lengths are
positive, nothing found later can make it shorter, so its distance is final.
Then *relax* each neighbour: if going through this location is shorter than
the neighbour's best distance, update it and remember where we came from.

**Steps in the code.**

1. `distance[source] = 0`, push `(source, 0)` into a min `PriorityQueue`.
2. Poll the smallest entry `u`. If `u` was already visited, skip it (an old,
   longer entry). Otherwise mark it visited.
3. If `u` is the destination, stop early.
4. For each road `u -> v` of length `w`: if `distance[u] + w < distance[v]`,
   set `distance[v]`, set `previous[v] = u`, and push `(v, new distance)`.
5. Rebuild the route by following `previous` back from the destination, then
   reverse the list.

**Special cases.** Same start and destination: route of one stop, 0 m.
Unknown location: a "not found" result with a message. Unreachable
destination: the queue empties before the destination is reached, so it has
no distance, and the result says no road connects them.

**Complexity.** Every push and poll on the heap costs O(log V); each road can
cause at most one push. Time O((V + E) log V), memory O(V + E).

**Why not BFS?** BFS finds the route with the fewest roads, not the shortest
distance. The test `findsTheShortestRouteNotTheFewestRoads` shows the
difference: A-B-D is 2 roads and 9 m, A-C-B-D is 3 roads and 8 m.

**Why it fails with negative weights.** A location's distance is treated as
final when it is polled. A negative road found later could lower it, which the
algorithm never revisits. Road lengths are never negative, so this is safe.

**Tie-breaking.** If two entries have the same distance, the one with the
smaller id is polled first, so the same input always produces the same route.

## 4. Trie (prefix tree)

File: `backend/src/main/java/campusrider/algorithms/LocationTrie.java`

**What it is.** A tree in which each edge is one character. The path from the
root to a node spells a prefix. Each node keeps its children in a
`TreeMap<Character, TrieNode>` and a list of the locations whose key ends there.

**Insert.** For each location we insert its full name, each word of the name,
and each alias, all in lower case. That is how "gate" finds Dairy Gate,
Prantik Gate and Bishmile Gate, and "tsc" finds Central Cafeteria (TSC).

**Search.** Walk down one character at a time. If a character is missing,
there are no matches. Otherwise do a depth-first walk of the subtree below
that node and collect all locations. A map keyed by location id removes
duplicates, because one place can be reached by several keys.

**Complexity.** Insert O(L) for a key of length L. Search O(P + N): P to walk
the prefix, N for the nodes in the subtree. The time does not depend on how
many other places start with different letters.

**Why not loop over every name with `startsWith`?** That checks every location
on every key press, O(V x L). A trie only looks at the branch for the typed
prefix, and it is the standard structure for autocomplete.

Empty or blank input returns no results, and only locations that exist in the
graph are ever inserted, so search can only return real places.

## 5. Queue (FIFO) for normal requests

File: `backend/src/main/java/campusrider/algorithms/RideQueue.java`

A queue is First In, First Out: the earliest request is processed first, which
is fair. Normal requests go into an `ArrayDeque`: `offer` adds at the back and
`poll` removes from the front, both O(1).

## 6. PriorityQueue for emergency requests

Same file. Emergency requests (an EMERGENCY account, or any passenger who
switches on "Emergency ride") go into a `PriorityQueue`, a binary min-heap
ordered by:

1. Priority: emergency = 0, then the passenger type (STAFF before STUDENT).
2. For the same priority, the smaller request id (the earlier request) first.

`poll()` always empties the emergency heap before touching the FIFO queue, so
an emergency is dispatched before every normal request. `offer` and `poll` are
O(log n). Iterating a `PriorityQueue` is not sorted, so `inProcessingOrder()`
polls a copy.

**Where it is used.** `RideService.dispatch()` drains the queue in this order and
offers each request to a rickshaw. Seats promised to earlier offers are
reserved, so when seats are scarce the queue order decides who gets them
(tested in `RideFlowTest.emergencyRequestIsDispatchedBeforeEarlierNormalRequests`).

## 7. Route compatibility (shared rides, max 3 seats)

File: `backend/src/main/java/campusrider/algorithms/RouteCompatibility.java`

For each rickshaw and a new passenger (pickup P, drop-off D, seats s):

1. **Seats**: free seats (minus seats promised to other offers) must be >= s.
2. **Pickup**: Dijkstra distance rickshaw -> P must be <= 2500 m.
3. **Baseline**: the best stop order for the passengers already on board.
4. **With new**: the best stop order after adding P and D.
5. **Additional distance** = withNew - baseline; **detour** = the largest delay
   to any existing passenger's arrival.
6. **Fairness**: in the chosen order every passenger rides at most 750 m more
   than their own shortest path, and the new passenger's wait is within range.
7. Compatible only if every check passes.

**Stop order search.** With k passengers there are at most 2k <= 6 stops. A
depth-first search tries every order in which each pickup comes before its own
drop-off (at most 6!/2^3 = 90 orders), with branch-and-bound pruning and
memoised Dijkstra legs. It is exact, and tiny because k <= 3.

The same planner gives the order the driver drives (`RidePlanner`), so the map,
the detour calculation and the simulated movement always agree.

## 8. Greedy matching + automatic fallback

Files: `algorithms/GreedyRickshawMatcher.java`, `matching/RideMatchingService.java`

Score (lower is better):

```
score = pickupDistance + 1.5 * detour - 100 * [pickup on current route]
        + 20 * (freeSeats - seatsNeeded)
```

The request is sent to the lowest score (greedy choice). If that driver rejects,
or does not answer within 60 s, the driver is added to the request's
`rejectedBy` set and the next best rickshaw is chosen automatically. When no
suitable rickshaw remains the request becomes REJECTED. Cost per decision:
O(R x Dijkstra work).

## 9. Custom Merge Sort

Files: `algorithms/MergeSort.java`, `algorithms/RickshawSorter.java`

Top-down merge sort, O(N log N) in every case, stable. It sorts the passenger's
list of available rickshaws by best match, nearest pickup or most seats
(compatible rickshaws always first), and also ride history and Trie results.
No `Collections.sort`, `List.sort` or `Arrays.sort` is used.

## 10. Ride lifecycle (state machine)

File: `backend/src/main/java/campusrider/algorithms/RideStateMachine.java`

```
PENDING -> ACCEPTED -> STARTED -> COMPLETED
PENDING -> REJECTED
PENDING | ACCEPTED -> CANCELLED
```

The transition table is an `EnumMap<RideStatus, EnumSet<RideStatus>>`.
`RideRequest.moveTo` calls `RideStateMachine.transition`, which throws
`InvalidStateTransitionException` (HTTP 409) for any other change. Starting a
ride also requires the rickshaw to be at the pickup; completing requires it to
be at the destination. Old CSV statuses MATCHED / IN_PROGRESS are read as
ACCEPTED / STARTED.

## 11. ArrayList and HashMap

Files: `backend/src/main/java/campusrider/repository/*.java`

- `PassengerRepository` / `DriverRepository`: HashMap by id and by username
  (O(1) login and duplicate checks) plus an ArrayList in file order.
- `RickshawRepository`: LinkedHashMap by rickshaw id.
- `RideRepository`: LinkedHashMap ride id -> ride, and a HashMap
  passengerId -> ArrayList of ride ids (passenger-to-ride mapping for the
  current ride and history).

## 12. Why CSV files instead of a database

- The data is small (25 places, 33 roads, a few rickshaws, the rides).
- The project is about data structures: all searching, sorting and matching is
  done by our own Java code, not by SQL.
- CSV files open in any editor or spreadsheet, which makes demonstrating easy.

Safety measures in `storage/CsvStorage.java`:
- RFC 4180 quoting (commas, quotes, line breaks inside values).
- Atomic writes: write `<file>.tmp`, force it to disk, then rename it.
- All reads and writes are synchronized, and every ride change happens inside
  one lock (`RideLock`), so concurrent bookings cannot overbook a rickshaw.
- An OS file lock stops two servers from using the same data folder.
- Invalid or duplicate rows are skipped, and the original file is copied to `.bak`.

## 13. Complexity summary

| Operation                       | Structure             | Time                |
|---------------------------------|-----------------------|---------------------|
| Neighbours of a location        | Adjacency list        | O(degree)           |
| Shortest route                  | Dijkstra + heap       | O((V + E) log V)    |
| Connectivity check (startup)    | BFS                   | O(V + E)            |
| Prefix search                   | Trie                  | O(P + N)            |
| Add / take normal request       | ArrayDeque            | O(1)                |
| Add / take emergency request    | PriorityQueue         | O(log n)            |
| Compatibility of one rickshaw   | DFS over stop orders  | O(S! S) for S <= 6  |
| Rank R rickshaws                | Merge sort            | O(R log R)          |
| Pick the best rickshaw          | Greedy scan           | O(R)                |
| Status change check             | EnumMap + EnumSet     | O(1)                |

## 14. Likely questions

- *Why a PriorityQueue in Dijkstra?* To take the closest unvisited location in
  O(log V) instead of scanning all V locations (O(V^2) overall).
- *Why can a location appear in the heap several times?* Java's PriorityQueue
  cannot lower a key, so we push a new entry and skip stale ones (`visited`).
- *Why not just assign any rickshaw with a free seat?* A shared rickshaw going
  the other way would make everyone late. RouteCompatibility rejects any
  rickshaw where the plan breaks the detour limits.
- *What stops two drivers accepting the last seat at once?* Every accept runs
  inside `RideLock` and re-checks seats and route before changing anything.
- *Is the tracking real GPS?* No. Rickshaws move along graph edges in a
  simulation (manual "Move" or automatic drive), and positions are pushed to
  browsers over a WebSocket.
- *What if a CSV file is damaged?* Invalid rows are skipped, the original is
  copied to `.bak`, and the server logs a warning.
