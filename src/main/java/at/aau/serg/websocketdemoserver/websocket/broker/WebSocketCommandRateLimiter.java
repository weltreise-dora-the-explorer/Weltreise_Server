package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

@Component
public class WebSocketCommandRateLimiter {

    public static final int GENERAL_LIMIT = 20;
    public static final long GENERAL_WINDOW_NANOS = Duration.ofSeconds(1).toNanos();
    public static final int LOBBY_ACCESS_LIMIT = 5;
    public static final long LOBBY_ACCESS_WINDOW_NANOS = Duration.ofSeconds(10).toNanos();

    private static final long IDLE_ENTRY_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int CLEANUP_INTERVAL = 256;
    private static final Set<CommandType> LOBBY_ACCESS_COMMANDS = EnumSet.of(
            CommandType.CREATE_LOBBY,
            CommandType.JOIN_LOBBY,
            CommandType.REJOIN_LOBBY
    );

    private final ConcurrentHashMap<String, RequestWindow> generalWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestWindow> lobbyAccessWindows = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger();
    private final LongSupplier nanoTime;

    public WebSocketCommandRateLimiter() {
        this(System::nanoTime);
    }

    WebSocketCommandRateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public void check(String sessionId, CommandType commandType) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        long now = nanoTime.getAsLong();
        requireWithinLimit(generalWindows, sessionId, GENERAL_LIMIT, GENERAL_WINDOW_NANOS, now);

        if (LOBBY_ACCESS_COMMANDS.contains(commandType)) {
            requireWithinLimit(
                    lobbyAccessWindows,
                    sessionId,
                    LOBBY_ACCESS_LIMIT,
                    LOBBY_ACCESS_WINDOW_NANOS,
                    now
            );
        }

        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            removeIdleEntries(now);
        }
    }

    private void requireWithinLimit(ConcurrentHashMap<String, RequestWindow> windows,
                                    String sessionId,
                                    int limit,
                                    long windowNanos,
                                    long now) {
        RequestWindow window = windows.computeIfAbsent(sessionId, ignored -> new RequestWindow());
        if (!window.tryAcquire(limit, windowNanos, now)) {
            throw new GameException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many commands. Please try again shortly"
            );
        }
    }

    private void removeIdleEntries(long now) {
        generalWindows.entrySet().removeIf(entry -> entry.getValue().isIdle(now));
        lobbyAccessWindows.entrySet().removeIf(entry -> entry.getValue().isIdle(now));
    }

    private static final class RequestWindow {
        private final Deque<Long> requestTimes = new ArrayDeque<>();
        private volatile long lastSeenNanos;

        synchronized boolean tryAcquire(int limit, long windowNanos, long now) {
            long cutoff = now - windowNanos;
            while (!requestTimes.isEmpty() && requestTimes.peekFirst() <= cutoff) {
                requestTimes.removeFirst();
            }

            lastSeenNanos = now;
            if (requestTimes.size() >= limit) {
                return false;
            }

            requestTimes.addLast(now);
            return true;
        }

        boolean isIdle(long now) {
            return now - lastSeenNanos > IDLE_ENTRY_NANOS;
        }
    }
}
