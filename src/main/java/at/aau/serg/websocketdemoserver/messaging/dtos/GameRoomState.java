package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import at.aau.serg.websocketdemoserver.game.models.PlayerState;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO und In-Memory-Zustand eines konkreten Spiels/einer Lobby.
 * Enthält Spieler, aktuelle Phase, Würfelergebnis sowie eine Versionierung für UI-Updates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRoomState {
    private String lobbyId;
    private String hostId;
    private List<PlayerState> players = new ArrayList<>();
    private GamePhase phase = GamePhase.LOBBY;
    private String currentPlayerId;
    private Integer lastDiceValue;
    private long version = 0L;
    private List<String> validMoveIds = new ArrayList<>();

    public GameRoomState(String lobbyId, String hostId, List<PlayerState> players,
                         GamePhase phase, String currentPlayerId, Integer lastDiceValue, long version) {
        this(lobbyId, hostId, players, phase, currentPlayerId, lastDiceValue, version, new ArrayList<>());
    }
}
