package pt.arrumo.events;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pt.arrumo.errors.ApiError;
import pt.arrumo.map.Dtos.MapChange;

/**
 * Real time with Server-Sent Events: every open screen keeps one HTTP connection and receives changes
 * as soon as they are committed. Works behind any HTTPS proxy and the browser reconnects on its own.
 * Enough for a single app instance; with several, this would switch to PostgreSQL LISTEN/NOTIFY.
 */
@Component
public class EventChannel {

    private static final Logger log = LoggerFactory.getLogger(EventChannel.class);
    private static final int MAX_SUBSCRIBERS = 300;
    private static final long DURATION = Duration.ofMinutes(30).toMillis();

    /** seesRawMaterial = false for logistics users: they get nothing from the raw-material room. */
    private record Subscriber(SseEmitter emitter, boolean seesRawMaterial) {}

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter open(boolean seesRawMaterial) {
        if (subscribers.size() >= MAX_SUBSCRIBERS)
            throw new ApiError(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Demasiadas ligações abertas.");
        SseEmitter e = new SseEmitter(DURATION);
        Subscriber s = new Subscriber(e, seesRawMaterial);
        subscribers.add(s);
        e.onCompletion(() -> subscribers.remove(s));
        e.onTimeout(() -> { subscribers.remove(s); e.complete(); });
        e.onError(x -> subscribers.remove(s));
        try {
            e.send(SseEmitter.event().name("connected").data("{}", MediaType.APPLICATION_JSON).reconnectTime(3000));
        } catch (IOException x) {
            subscribers.remove(s);
        }
        return e;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(MapChange change) {
        for (Subscriber s : subscribers) {
            if (!s.seesRawMaterial() && "RM".equals(change.area())) continue; // logistics does not see raw material
            send(s, "change", change);
        }
    }

    private void send(Subscriber s, String name, Object payload) {
        try {
            s.emitter().send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException x) {
            subscribers.remove(s);
            s.emitter().completeWithError(x);
        }
    }

    /** Keeps connections alive (proxies tend to close idle ones). */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        for (Subscriber s : subscribers) {
            try {
                s.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException x) {
                subscribers.remove(s);
            }
        }
        if (log.isDebugEnabled()) log.debug("{} screens connected", subscribers.size());
    }
}
