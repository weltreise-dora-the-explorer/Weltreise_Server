package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Service für das Verwalten der Spieler-Lobbys (Beitreten, Verlassen, Starten des Spiels).
 */
@Service
public class LobbyService {
    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int MAX_PLAYERS_IN_LOBBY = 4;

    private final InMemoryLobbyStore lobbyStore;
    private final CityDistributor cityDistributor;
    private final Random random = new Random();

    public LobbyService(InMemoryLobbyStore lobbyStore, CityDistributor cityDistributor) {
        this.lobbyStore = Objects.requireNonNull(lobbyStore, "lobbyStore must not be null");
        this.cityDistributor = Objects.requireNonNull(cityDistributor, "cityDistributor must not be null");
    }

    public GameRoomState createLobby(String lobbyId, String playerId) {
        return createLobby(lobbyId, playerId, null);
    }

    public GameRoomState createLobby(String lobbyId, String playerId, String clientId) {
        validatePlayerId(playerId);
        if (lobbyStore.get(lobbyId).isPresent()) {
            throw new GameException(ErrorCode.GAME_ALREADY_STARTED, "Lobby already exists");
        }
        GameRoomState newLobby = new GameRoomState();
        newLobby.setLobbyId(lobbyId);
        newLobby.setHostId(playerId);
        newLobby.getPlayers().add(new PlayerState(playerId, clientId));
        lobbyStore.put(lobbyId, newLobby);
        return newLobby;
    }

    public GameRoomState joinLobby(String lobbyId, String playerId) {
        return joinLobby(lobbyId, playerId, null);
    }

    public GameRoomState joinLobby(String lobbyId, String playerId, String clientId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId).orElseThrow(() ->
            new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby does not exist. Please check the Game PIN!")
        );

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new GameException(ErrorCode.CANNOT_JOIN_STARTED_GAME, "Cannot join started game");
        }
        if (state.getPlayers().size() >= MAX_PLAYERS_IN_LOBBY) {
            throw new GameException(ErrorCode.LOBBY_FULL, "Lobby is full");
        }
        if (containsPlayer(state.getPlayers(), playerId)) {
            throw new GameException(ErrorCode.PLAYER_ALREADY_JOINED, "Player already joined lobby");
        }

        state.getPlayers().add(new PlayerState(playerId, clientId));
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    /**
     * Markiert einen Spieler als disconnected, ohne ihn aus der Lobby zu entfernen.
     * Wird bei WebSocket-Disconnect aufgerufen; Spieler hat Grace Period zum Reconnect.
     */
    public GameRoomState markPlayerDisconnected(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        PlayerState player = findPlayer(state.getPlayers(), playerId)
                .orElseThrow(() -> new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Player is not in lobby"));

        player.setConnected(false);
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    /**
     * Bringt einen disconnecteten Spieler zurück in die Lobby.
     * Validiert clientId, setzt connected=true und gibt aktuellen State zurück.
     */
    public GameRoomState rejoinLobby(String lobbyId, String playerId, String clientId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        PlayerState player = findPlayer(state.getPlayers(), playerId)
                .orElseThrow(() -> new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Player is not in lobby"));

        if (clientId != null && player.getClientId() != null && !clientId.equals(player.getClientId())) {
            throw new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Client id mismatch");
        }

        if (player.getClientId() == null && clientId != null) {
            player.setClientId(clientId);
        }

        player.setConnected(true);
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    /**
     * Entfernt einen disconnecteten Spieler endgültig aus der Lobby (nach Grace Period).
     * Wirkt wie leaveLobby, aber wirft keine Exception falls Spieler/Lobby schon weg sind.
     */
    public LobbyLeaveResult removeDisconnectedPlayer(String lobbyId, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return new LobbyLeaveResult(null, false);
        }
        if (lobbyStore.get(lobbyId).isEmpty()) {
            return new LobbyLeaveResult(null, false);
        }
        try {
            return leaveLobby(lobbyId, playerId);
        } catch (GameException ex) {
            return new LobbyLeaveResult(null, false);
        }
    }

    public LobbyLeaveResult leaveLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        int leavingIndex = indexOfPlayer(state.getPlayers(), playerId);
        if (leavingIndex < 0) {
            throw new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Player is not in lobby");
        }

        if (playerId.equals(state.getHostId())) {
            state.getPlayers().clear();
            lobbyStore.remove(lobbyId);
            return new LobbyLeaveResult(state, true);
        }

        String previousCurrentPlayer = state.getCurrentPlayerId();
        state.getPlayers().remove(leavingIndex);
        state.setVersion(state.getVersion() + 1);

        if (state.getPlayers().isEmpty()) {
            lobbyStore.remove(lobbyId);
            return new LobbyLeaveResult(state, false);
        }

        if (previousCurrentPlayer != null && previousCurrentPlayer.equals(playerId)) {
            int nextIndex = leavingIndex % state.getPlayers().size();
            state.setCurrentPlayerId(state.getPlayers().get(nextIndex).getPlayerId());
            state.setLastDiceValue(null);
        }

        // Wenn waehrend eines laufenden Spiels nur noch 1 Spieler uebrig ist,
        // wird das Spiel beendet und in die Lobby-Phase zurueckgesetzt.
        // Der uebrige Spieler kann auf neue Mitspieler warten.
        if (state.getPlayers().size() == 1 && state.getPhase() != GamePhase.LOBBY) {
            resetGameToLobbyPhase(state);
        }

        return new LobbyLeaveResult(state, false);
    }

    /**
     * Setzt einen aktiven Spielzustand zurueck auf Lobby-Phase: Phase=LOBBY,
     * Spieler-Daten geleert (Staedte, Position), Spielzustaende verworfen.
     * Spieler selbst bleiben in der Lobby.
     */
    private void resetGameToLobbyPhase(GameRoomState state) {
        state.setPhase(GamePhase.LOBBY);
        state.setCurrentPlayerId(null);
        state.setLastDiceValue(null);
        state.getValidMoveIds().clear();
        state.setGameOver(false);
        for (PlayerState player : state.getPlayers()) {
            player.setStartCity(null);
            player.setCurrentCity(null);
            player.setPreviousCityId(null);
            player.setBoardPosition(0);
            player.setRemainingSteps(0);
            player.getOwnedCities().clear();
            player.getVisitedCities().clear();
        }
    }

    public GameRoomState startGame(String lobbyId, int stops) {
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new GameException(ErrorCode.GAME_ALREADY_STARTED, "Game already started");
        }
        if (state.getPlayers().size() < MIN_PLAYERS_TO_START) {
            throw new GameException(ErrorCode.MIN_PLAYERS_NOT_REACHED, "At least two players are required");
        }

        state.setPhase(GamePhase.IN_TURN);

        CityColor[] colors = CityColor.values();
        CityColor startColor = colors[random.nextInt(colors.length)];

        int amountPerColor = Math.max(1, stops / colors.length);
        List<City> allCities = cityDistributor.getAllCities();
        cityDistributor.distributeByColorRounds(allCities, state.getPlayers(), amountPerColor, startColor);

        for (PlayerState player : state.getPlayers()) {
            City start = player.getOwnedCities().removeFirst();
            player.setStartCity(start);
            player.setCurrentCity(start);
        }

        state.setCurrentPlayerId(state.getPlayers().getFirst().getPlayerId());
        state.setLastDiceValue(null);
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    public GameRoomState resetLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        if (!playerId.equals(state.getHostId())) {
            throw new GameException(ErrorCode.MISSING_PLAYER_ID, "Only the host can reset the lobby");
        }

        for (PlayerState player : state.getPlayers()) {
            player.setStartCity(null);
            player.setCurrentCity(null);
            player.setPreviousCityId(null);
            player.setBoardPosition(0);
            player.setRemainingSteps(0);
            player.getOwnedCities().clear();
            player.getVisitedCities().clear();
        }

        state.setPhase(GamePhase.LOBBY);
        state.setCurrentPlayerId(null);
        state.setLastDiceValue(null);
        state.getValidMoveIds().clear();
        state.setGameOver(false);
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

    private Optional<PlayerState> findPlayer(List<PlayerState> players, String playerId) {
        return players.stream()
                .filter(player -> playerId.equals(player.getPlayerId()))
                .findFirst();
    }

    private void validatePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new GameException(ErrorCode.MISSING_PLAYER_ID, "Player id is required");
        }
    }
}
