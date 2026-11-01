package campusrider.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after MAX_FAILURES failed logins for the same
 * account within WINDOW_MS, that account is locked for LOCK_MS.
 */
@Component
public class LoginAttemptLimiter {
    static final int MAX_FAILURES = 5;
    static final long WINDOW_MS = 5 * 60_000L;
    static final long LOCK_MS = 60_000L;

    private static final class Attempts {
        int failures;
        long firstFailureAt;
        long lockedUntil;
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** Seconds until the account may try again, or 0 when not locked. */
    public long secondsLocked(String key) {
        Attempts a = attempts.get(key);
        long now = System.currentTimeMillis();
        return a == null || a.lockedUntil <= now ? 0 : (a.lockedUntil - now + 999) / 1000;
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        attempts.compute(key, (k, a) -> {
            Attempts next = a == null || now - a.firstFailureAt > WINDOW_MS ? new Attempts() : a;
            if (next.failures == 0) {
                next.firstFailureAt = now;
            }
            next.failures++;
            if (next.failures >= MAX_FAILURES) {
                next.lockedUntil = now + LOCK_MS;
                next.failures = 0;
                next.firstFailureAt = 0;
            }
            return next;
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }
}
