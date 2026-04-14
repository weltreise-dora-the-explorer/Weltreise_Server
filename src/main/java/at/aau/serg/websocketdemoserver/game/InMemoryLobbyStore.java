package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory-Speicher (Map) zur Verwaltung aller aktiven GameRoomStates (Lobbys).
 */
@Component
public class InMemoryLobbyStore {
    private final Map<String, GameRoomState> lobbies = new ConcurrentHashMap<>();

    public GameRoomState getOrCreate(String lobbyId) {
        validateLobbyId(lobbyId);
        return lobbies.computeIfAbsent(lobbyId, id -> {
            GameRoomState state = new GameRoomState();
            state.setLobbyId(id);
            return state;
        });
    }

    public void put(String lobbyId, GameRoomState state) {
        validateLobbyId(lobbyId);
        lobbies.put(lobbyId, state);
    }

    public Optional<GameRoomState> get(String lobbyId) {
        validateLobbyId(lobbyId);
        return Optional.ofNullable(lobbies.get(lobbyId));
    }

    public void remove(String lobbyId) {
        validateLobbyId(lobbyId);
        lobbies.remove(lobbyId);
    }

    private void validateLobbyId(String lobbyId) {
        if (lobbyId == null || lobbyId.isBlank()) {
            throw new IllegalArgumentException("Lobby id is required");
        }
    }
}
