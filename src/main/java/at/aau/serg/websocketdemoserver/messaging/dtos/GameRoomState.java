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
    private Long serverNowMs;
    private List<String> validMoveIds = new ArrayList<>();

    private GameMode gameMode = GameMode.CITY_HOPPER;
    private boolean gameOver = false;
    private String minigameWinnerPlayerId;
    private String minigameLostCityName;
    private String minigameNewCityName;

    private List<String> reactionReadyPlayerIds = new ArrayList<>();
    private Long reactionReadyEndsAtMs;
    private Long reactionRoundEndsAtMs;
    private Long reactionStartTimeMs;
    private Long reactionButtonVisibleAtMs;
    private java.util.Map<String, Long> reactionPressTimesMs = new java.util.HashMap<>();

    public GameRoomState(String lobbyId, String hostId, List<PlayerState> players,
                         GamePhase phase, String currentPlayerId, Integer lastDiceValue, long version) {
        this(lobbyId, hostId, players, phase, currentPlayerId, lastDiceValue, version, null,  new ArrayList<>(), GameMode.CITY_HOPPER, false, null, null, null, new ArrayList<>(), null, null, null, null, new java.util.HashMap<>());
    }
}
