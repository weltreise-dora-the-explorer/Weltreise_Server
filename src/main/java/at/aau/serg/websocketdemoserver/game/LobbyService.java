package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.PlayerState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class LobbyService {
    private static final int MIN_PLAYERS_TO_START = 2;

    private final InMemoryLobbyStore lobbyStore;

    public LobbyService(InMemoryLobbyStore lobbyStore) {
        this.lobbyStore = Objects.requireNonNull(lobbyStore, "lobbyStore must not be null");
    }

    public GameRoomState joinLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.getOrCreate(lobbyId);

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new GameException(ErrorCode.CANNOT_JOIN_STARTED_GAME, "Cannot join started game");
        }
        if (containsPlayer(state.getPlayers(), playerId)) {
            throw new GameException(ErrorCode.PLAYER_ALREADY_JOINED, "Player already joined lobby");
        }

        state.getPlayers().add(new PlayerState(playerId, null, 0));
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
