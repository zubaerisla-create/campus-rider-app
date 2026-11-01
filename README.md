# Smart Campus Ride-Sharing and Route Optimizer (JU Campus)

A Java Swing desktop application for Uber-style rickshaw ride-sharing on the Jahangirnagar
University (JU) campus. Designed as a Data Structures and Algorithms (DSA) project with
two synchronized side-by-side dashboards (**Passenger Dashboard** and **Driver Dashboard**),
multi-passenger seat sharing (up to 3 seats), route compatibility evaluation, and simulated
live GPS tracking — all powered by CSV file storage without requiring any external database.

## 1. DSA Implementation Overview

| Feature in the Application | Data Structure / Algorithm | Source File |
|---|---|---|
| JU Campus Road Map | Weighted Undirected Graph (Adjacency List) | `src/main/java/dsa/CampusGraph.java` |
| Shortest Route Calculation | Dijkstra's Algorithm with `PriorityQueue` | `src/main/java/dsa/Dijkstra.java` |
| Location Search (Autocomplete) | Trie (Prefix Tree) | `src/main/java/dsa/LocationTrie.java` |
| Ride Request Queue (FIFO) | Queue (`ArrayDeque`) | `src/main/java/dsa/RideQueue.java` |
| Emergency Priority Queue | Min-Heap (`PriorityQueue`) | `src/main/java/dsa/RideQueue.java` |
| Multi-Passenger Route Compatibility | Dijkstra Detour & Route Overlap Evaluation | `src/main/java/dsa/RouteCompatibility.java` |
| Greedy Rickshaw Matching & Fallback | Greedy Algorithm + Automatic Reject Fallback | `src/main/java/service/RideMatchingService.java` |
| Available Rickshaw Sorting | Custom Merge Sort $O(N \log N)$ | `src/main/java/dsa/RickshawSorter.java` |
| Ride Status Lifecycle | Finite State Machine (`Pending -> Accepted -> Started -> Completed`) | `src/main/java/model/RideStatus.java` |
| Passenger, Driver & Rickshaw Lookup | `ArrayList` / `HashMap` / `LinkedHashMap` | `src/main/java/service/RideService.java` |

## 2. Key Features

### Passenger Dashboard (`PassengerWindow` / `PassengerPanel`)
- **Passenger Registration & Login**: Log in with existing accounts (`tanvir`, `nila`, `rafiq`, `emergency` — password `1234`) or register a new passenger account persisted to `data/passengers.csv`.
- **Pickup & Destination Selection**: Search campus locations via **Trie Prefix Search** or dropdowns and preview the exact Dijkstra shortest route on the 2D Campus Map.
- **Find Nearby & Compatible Rickshaws (Sorted via Merge Sort)**: View available rickshaws with free seats (1 to 3 seats), sorted by **Best Route Compatibility (Greedy)**, **Nearest to Pickup (Dijkstra)**, or **Most Available Seats**.
- **Seat Sharing (1–3 Seats) & Ride Request**: Request 1, 2, or 3 seats and send the request either automatically to the best compatible rickshaw or to a specific selected rickshaw.
- **Simulated Live GPS Tracking**: Watch the assigned rickshaw move stop-by-stop or smoothly along the campus road network as the driver accepts and drives the route.

### Driver Dashboard (`DriverWindow` / `DriverPanel`)
- **Driver Registration & Login**: Log in with existing driver accounts (`karim`, `mokbul`, `salam`, `rahim`, `jamal` — password `1234`) or register a new driver & rickshaw persisted to `data/drivers.csv` and `data/rickshaws.csv`.
- **Rickshaw Status Configuration**: Set the rickshaw's **Current Location**, **Target Destination**, and **Available Seats (0–3)** directly from the dashboard.
- **Accept / Reject Ride Requests**:
  - **Accept**: Assigns the passenger to the rickshaw (supporting multi-passenger seat sharing up to 3 seats) and calculates the route detour.
  - **Reject**: Declines the request for the current driver and **automatically finds and forwards the request to the next most compatible rickshaw** using the Greedy Route Compatibility algorithm.
- **Simulated Live GPS Movement**: Press **Move Rickshaw (Next Stop)** or **Start Live GPS Auto-Drive** to move the rickshaw along the Dijkstra shortest path while updating the Passenger Window in real time.

## 3. CSV File-Based Storage (`data/`)

All application state is persisted in human-readable CSV files inside the `data/` directory:

| File | Columns | Description |
|---|---|---|
| `locations.csv` | `id,name,category,rickshawStand,aliases` | 25 JU campus locations and aliases |
| `roads.csv` | `from,to,distanceMeters,roadName` | 33 two-way campus road segments |
| `passengers.csv` | `id,username,password,fullName,passengerType,phone` | Registered passenger accounts |
| `drivers.csv` | `id,username,password,driverName,rickshawId,phone` | Registered driver accounts |
| `rickshaws.csv` | `id,driverName,locationId,status` | Campus rickshaws and current locations |
| `rides.csv` | `id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId` | All ride requests and statuses |
| `ride_passengers.csv` | `rideId,passengerId,passengerName,rickshawId,pickupId,destinationId,seatsBooked,detourMeters,status` | Multi-passenger seat-sharing & detour records |

## 4. Build, Test, and Run

Requirements: **Java 17+** and **Maven 3.6+**.

```bash
mvn clean package          # compiles, runs all JUnit 5 tests, builds target/campus-ride.jar
java -jar target/campus-ride.jar
```

On startup, the application opens both the **Main / Passenger Window** and the **Driver Window** side-by-side on your desktop so you can demonstrate the complete Passenger ↔ Driver workflow on a single computer.
