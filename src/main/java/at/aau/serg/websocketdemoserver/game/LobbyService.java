package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Service für das Verwalten der Spieler-Lobbys (Beitreten, Verlassen, Starten des Spiels).
 */
@Service
public class LobbyService {
    private static final int MIN_PLAYERS_TO_START = 2;

    private final InMemoryLobbyStore lobbyStore;
    private final CityDistributor cityDistributor;

    public LobbyService(InMemoryLobbyStore lobbyStore, CityDistributor cityDistributor) {
        this.lobbyStore = Objects.requireNonNull(lobbyStore, "lobbyStore must not be null");
        this.cityDistributor = Objects.requireNonNull(cityDistributor, "cityDistributor must not be null");
    }

    public GameRoomState createLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        if (lobbyStore.get(lobbyId).isPresent()) {
            throw new GameException(ErrorCode.GAME_ALREADY_STARTED, "Lobby already exists");
        }
        GameRoomState newLobby = new GameRoomState();
        newLobby.setLobbyId(lobbyId);
        newLobby.getPlayers().add(new PlayerState(playerId));
        lobbyStore.put(lobbyId, newLobby);
        return newLobby;
    }

    public GameRoomState joinLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId).orElseThrow(() ->
            new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby does not exist. Please check the Game PIN!")
        );

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new GameException(ErrorCode.CANNOT_JOIN_STARTED_GAME, "Cannot join started game");
        }
        if (containsPlayer(state.getPlayers(), playerId)) {
            throw new GameException(ErrorCode.PLAYER_ALREADY_JOINED, "Player already joined lobby");
        }

        state.getPlayers().add(new PlayerState(playerId));
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    public GameRoomState leaveLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        int leavingIndex = indexOfPlayer(state.getPlayers(), playerId);
        if (leavingIndex < 0) {
            throw new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Player is not in lobby");
        }

        String previousCurrentPlayer = state.getCurrentPlayerId();
        state.getPlayers().remove(leavingIndex);
        state.setVersion(state.getVersion() + 1);

        if (state.getPlayers().isEmpty()) {
            lobbyStore.remove(lobbyId);
            return state;
        }

        if (previousCurrentPlayer != null && previousCurrentPlayer.equals(playerId)) {
            int nextIndex = leavingIndex % state.getPlayers().size();
            state.setCurrentPlayerId(state.getPlayers().get(nextIndex).getPlayerId());
            state.setLastDiceValue(null);
        }

        return state;
    }

    public GameRoomState startGame(String lobbyId) {
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new GameException(ErrorCode.GAME_ALREADY_STARTED, "Game already started");
        }
        if (state.getPlayers().size() < MIN_PLAYERS_TO_START) {
            throw new GameException(ErrorCode.MIN_PLAYERS_NOT_REACHED, "At least two players are required");
        }

        state.setPhase(GamePhase.IN_TURN);
        
        // 1. Give each player their required cities (2 per continent = 8 total initially)
        // Wir holen die Städte jetzt aus dem geladenen JSON-Speicher!
        List<City> allCities = cityDistributor.getAllCities();
        cityDistributor.distributeByContinent(allCities, state.getPlayers(), 2);

        // 2. Setup initial positions (use first target city as start city for sprint 1 simplicity)
        for (PlayerState player : state.getPlayers()) {
            if (!player.getOwnedCities().isEmpty()) {
                City start = player.getOwnedCities().getFirst();
                player.setStartCity(start);
                player.setCurrentCity(start);
            }
        }

        state.setCurrentPlayerId(state.getPlayers().getFirst().getPlayerId());
        state.setLastDiceValue(null);
        state.setVersion(state.getVersion() + 1);
        return state;
    }



    private boolean containsPlayer(List<PlayerState> players, String playerId) {
        return players.stream().anyMatch(player -> playerId.equals(player.getPlayerId()));
    }

    private int indexOfPlayer(List<PlayerState> players, String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (playerId.equals(players.get(i).getPlayerId())) {
                return i;
            }
        }
        return -1;
    }

    private void validatePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new GameException(ErrorCode.MISSING_PLAYER_ID, "Player id is required");
        }
    }
}
