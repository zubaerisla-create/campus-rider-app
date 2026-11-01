package campusrider.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One fair lock around every change to rides, seats and rickshaw positions.
 *
 * Booking, accepting, cancelling and the simulated drive all run inside it, so
 * two drivers can never accept the last seat at the same time, a seat count can
 * never be read half-updated, and CSV files are written in a consistent state.
 * Ride operations are small (microseconds of work plus a small file write), so
 * a single lock is simpler and safer than fine-grained locking.
 */
@Component
public class RideLock {
    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T call(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
