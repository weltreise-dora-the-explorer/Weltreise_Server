package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory-Speicher (Map) zur Verwaltung aller aktiven GameRoomStates (Lobbys).
 * Persistiert optional jeden Schreibvorgang ueber {@link LobbyPersistence}.
 */
@Component
public class InMemoryLobbyStore {
    private final Map<String, GameRoomState> lobbies = new ConcurrentHashMap<>();
    private final LobbyPersistence persistence;

    public InMemoryLobbyStore() {
        this(null);
    }

    @Autowired(required = false)
    public InMemoryLobbyStore(LobbyPersistence persistence) {
        this.persistence = persistence;
    }

    @PostConstruct
    void loadFromPersistence() {
        if (persistence != null) {
            lobbies.putAll(persistence.loadAll());
        }
    }

    public GameRoomState getOrCreate(String lobbyId) {
        validateLobbyId(lobbyId);
        boolean[] created = { false };
        GameRoomState result = lobbies.computeIfAbsent(lobbyId, id -> {
            created[0] = true;
            GameRoomState state = new GameRoomState();
            state.setLobbyId(id);
            return state;
        });
        if (created[0]) {
            triggerSave();
        }
        return result;
    }

    public void put(String lobbyId, GameRoomState state) {
        validateLobbyId(lobbyId);
        lobbies.put(lobbyId, state);
        triggerSave();
    }

    public Optional<GameRoomState> get(String lobbyId) {
        validateLobbyId(lobbyId);
        return Optional.ofNullable(lobbies.get(lobbyId));
    }

    public void remove(String lobbyId) {
        validateLobbyId(lobbyId);
        lobbies.remove(lobbyId);
        triggerSave();
    }

    /**
     * Persistiert den aktuellen Stand der Map.
     * Aufruf noetig, wenn ein GameRoomState in-place mutiert wurde
     * (z.B. Spieler joined, dice geworfen), ohne dass put/remove aufgerufen wurde.
     */
    public void save() {
        triggerSave();
    }

    private void triggerSave() {
        if (persistence != null) {
            persistence.saveAll(lobbies);
        }
    }

    private void validateLobbyId(String lobbyId) {
        if (lobbyId == null || lobbyId.isBlank()) {
            throw new IllegalArgumentException("Lobby id is required");
        }
    }
}
