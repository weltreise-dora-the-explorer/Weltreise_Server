package at.aau.serg.websocketdemoserver.websocket.broker;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    public record SessionInfo(String lobbyId, String playerId) {}

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String lobbyId, String playerId) {
        sessions.put(sessionId, new SessionInfo(lobbyId, playerId));
    }

    public Optional<SessionInfo> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
