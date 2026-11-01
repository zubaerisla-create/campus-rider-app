import { Button } from '../components/ui'

const DSA = [
  ['Campus road network', 'Weighted graph (adjacency list) + BFS connectivity check', 'algorithms/CampusGraph.java'],
  ['Shortest routes, pickup distance, detours', "Dijkstra's algorithm (binary-heap PriorityQueue)", 'algorithms/Dijkstra.java'],
  ['Pickup / destination autocomplete', 'Trie (prefix tree)', 'algorithms/LocationTrie.java'],
  ['Normal ride requests', 'Queue (FIFO, ArrayDeque)', 'algorithms/RideQueue.java'],
  ['Emergency ride requests', 'Priority Queue (min-heap)', 'algorithms/RideQueue.java'],
  ['Choosing the driver to send a request to', 'Greedy selection with a documented score', 'algorithms/GreedyRickshawMatcher.java'],
  ['Sorting available rickshaws', 'Custom Merge Sort, O(N log N)', 'algorithms/MergeSort.java, RickshawSorter.java'],
  ['Ride lifecycle', 'Finite state machine', 'algorithms/RideStateMachine.java'],
  ['Accounts, rickshaws, rides, passenger-to-ride map', 'HashMap / ArrayList repositories', 'repository/*.java'],
  ['Shared rides (max 3 seats)', 'Route compatibility: stop-order search + detour limits', 'algorithms/RouteCompatibility.java'],
]

export default function About() {
  return (
    <div className="mx-auto max-w-4xl px-5 py-14">
      <p className="text-sm font-semibold text-accent">About</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight md:text-4xl">Campus Rider</h1>
      <p className="mt-4 text-lg text-muted">
        Campus Rider is a ride-sharing web app for battery and pedal rickshaws on the Jahangirnagar University campus.
        Passengers book seats, drivers accept requests, and up to three passengers going the same way can share one
        rickshaw.
      </p>

      <div className="mt-10 grid gap-4 md:grid-cols-3">
        <Info title="Shared seats">A rickshaw carries at most three passengers. A new passenger is added only if the route still fits everyone.</Info>
        <Info title="Fair matching">Requests are sent to the best-suited driver. If they pass, the request moves to the next one automatically.</Info>
        <Info title="Simulated tracking">Rickshaw movement is simulated along real campus roads. It is not GPS.</Info>
      </div>

      <h2 className="mt-14 text-xl font-bold">Under the hood</h2>
      <p className="mt-2 text-muted">
        Campus Rider started as a Data Structures &amp; Algorithms course project. All the algorithms below are written by
        hand in the Java Spring Boot backend and power real features. The React frontend only displays their results.
      </p>
      <div className="mt-6 overflow-hidden rounded-2xl border border-line">
        <table className="w-full text-left text-sm">
          <thead className="bg-canvas text-muted">
            <tr>
              <th className="px-4 py-3 font-medium">Feature</th>
              <th className="px-4 py-3 font-medium">Data structure / algorithm</th>
              <th className="hidden px-4 py-3 font-medium md:table-cell">Java source</th>
            </tr>
          </thead>
          <tbody>
            {DSA.map(([f, a, s]) => (
              <tr key={f} className="border-t border-line align-top">
                <td className="px-4 py-3 font-medium">{f}</td>
                <td className="px-4 py-3 text-ink-soft">{a}</td>
                <td className="hidden px-4 py-3 font-mono text-xs text-muted md:table-cell">{s}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-4 text-sm text-muted">All data is stored in CSV files on the server. There is no database.</p>

      <div className="mt-12 flex flex-wrap gap-3">
        <Button to="/register/passenger">Start riding</Button>
        <Button to="/register/driver" variant="outline">Become a driver</Button>
      </div>
    </div>
  )
}

function Info({ title, children }) {
  return (
    <div className="rounded-2xl bg-canvas p-5">
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-1.5 text-sm text-muted">{children}</p>
    </div>
  )
}
