package campusrider.algorithms;

import campusrider.model.RideRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * The dispatch queue for ride requests that are waiting for a driver.
 *
 *   normal    - Queue (ArrayDeque), FIFO: first come, first served.   offer/poll O(1)
 *   emergency - PriorityQueue (binary min-heap) ordered by priority,
 *               then by request id (earlier first).                  offer/poll O(log n)
 *
 * poll() always serves the emergency heap first, so an emergency request is
 * dispatched before every normal request regardless of arrival time.
 *
 * Used by: RideService - every new request and every request that needs a new
 * driver (after a rejection, an expired offer, or a driver going offline) is
 * added here, and the dispatcher polls requests in this order when handing out
 * offers. Seats promised to earlier requests are reserved, so the order decides
 * who gets a scarce rickshaw.
 */
public class RideQueue {

    /** Lower priority number first; for equal priority the earlier request (smaller id) first. */
    public static final Comparator<RideRequest> PRIORITY_ORDER =
            Comparator.comparingInt(RideRequest::getPriority).thenComparingInt(RideRequest::getId);

    private final Queue<RideRequest> normal = new ArrayDeque<>();
    private final PriorityQueue<RideRequest> emergency = new PriorityQueue<>(PRIORITY_ORDER);

    /** Adds a request to the right queue. A request already waiting is not added twice. */
    public synchronized boolean add(RideRequest request) {
        if (request == null || contains(request)) {
            return false;
        }
        return request.isEmergency() ? emergency.offer(request) : normal.offer(request);
    }

    /** The next request to process without removing it: emergency first, then FIFO. */
    public synchronized RideRequest peek() {
        return emergency.isEmpty() ? normal.peek() : emergency.peek();
    }

    /** Removes and returns the next request: emergency first, then FIFO. */
    public synchronized RideRequest poll() {
        return emergency.isEmpty() ? normal.poll() : emergency.poll();
    }

    /** Removes a specific request, for example when it is cancelled or accepted. */
    public synchronized boolean remove(RideRequest request) {
        return emergency.remove(request) | normal.remove(request);
    }

    public synchronized boolean contains(RideRequest request) {
        return emergency.contains(request) || normal.contains(request);
    }

    public synchronized int size() {
        return emergency.size() + normal.size();
    }

    public synchronized int emergencySize() {
        return emergency.size();
    }

    public synchronized boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Removes every waiting request and returns them in processing order.
     * The caller re-adds the ones that could not be served.
     */
    public synchronized List<RideRequest> drainInOrder() {
        List<RideRequest> ordered = new ArrayList<>();
        RideRequest next;
        while ((next = poll()) != null) {
            ordered.add(next);
        }
        return ordered;
    }

    /**
     * Requests in the order they would be processed, without changing the queue.
     * A PriorityQueue's iterator is not sorted, so we poll a copy of it instead.
     */
    public synchronized List<RideRequest> inProcessingOrder() {
        PriorityQueue<RideRequest> emergencyCopy = new PriorityQueue<>(PRIORITY_ORDER);
        emergencyCopy.addAll(emergency);
        List<RideRequest> ordered = new ArrayList<>();
        while (!emergencyCopy.isEmpty()) {
            ordered.add(emergencyCopy.poll());
        }
        ordered.addAll(normal);
        return ordered;
    }
}
