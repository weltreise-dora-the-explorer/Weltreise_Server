package at.aau.serg.websocketdemoserver.websocket.broker;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Verwaltet die 60-Sekunden Grace-Period-Timer fuer disconnected Spieler.
 * Nach Ablauf wird der per Callback uebergebene Cleanup-Code ausgefuehrt
 * (typischerweise: Spieler endgueltig aus der Lobby entfernen).
 */
@Service
public class DisconnectScheduler {

    public static final long GRACE_PERIOD_SECONDS = 60L;

    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public DisconnectScheduler() {
        this(Executors.newSingleThreadScheduledExecutor());
    }

    DisconnectScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Startet einen Grace-Period-Timer (60s) fuer den Spieler.
     * Wird kein cancel() aufgerufen, laeuft der Callback ab.
     */
    public void schedule(String lobbyId, String playerId, Runnable onTimeout) {
        schedule(lobbyId, playerId, onTimeout, GRACE_PERIOD_SECONDS);
    }

    /**
     * Variante mit konfigurierbarer Verzoegerung (vor allem fuer Tests).
     */
    public void schedule(String lobbyId, String playerId, Runnable onTimeout, long delaySeconds) {
        String key = key(lobbyId, playerId);
        cancel(lobbyId, playerId);
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                onTimeout.run();
            } finally {
                tasks.remove(key);
            }
        }, delaySeconds, TimeUnit.SECONDS);
        tasks.put(key, future);
    }

    /**
     * Bricht den Timer fuer den Spieler ab (z.B. bei Reconnect).
     * Returnt true wenn ein Timer abgebrochen wurde.
     */
    public boolean cancel(String lobbyId, String playerId) {
        String key = key(lobbyId, playerId);
        ScheduledFuture<?> future = tasks.remove(key);
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    /**
     * Returnt true wenn ein Timer fuer diesen Spieler aktiv ist.
     */
    public boolean isScheduled(String lobbyId, String playerId) {
        return tasks.containsKey(key(lobbyId, playerId));
    }

    @PreDestroy
    public void shutdown() {
        tasks.values().forEach(future -> future.cancel(false));
        tasks.clear();
        executor.shutdownNow();
    }

    private String key(String lobbyId, String playerId) {
        return lobbyId + ":" + playerId;
    }
}
