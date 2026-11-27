package pt.arrumo.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Slows down brute-force attacks from one IP, even against usernames that do not exist.
 * (Per-account lockout lives in the database; this one is per address.)
 */
@Component
public class LoginThrottle {

    private static final int MAX_PER_IP = 30;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean locked(String ip) {
        Deque<Instant> d = failures.get(ip);
        if (d == null) return false;
        synchronized (d) {
            clear(d, Instant.now());
            return d.size() >= MAX_PER_IP;
        }
    }

    public void failed(String ip) {
        Deque<Instant> d = failures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(Instant.now());
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void cleanUp() {
        Instant now = Instant.now();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                clear(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static void clear(Deque<Instant> d, Instant now) {
        while (!d.isEmpty() && d.peekFirst().isBefore(now.minus(WINDOW))) d.pollFirst();
    }
}
