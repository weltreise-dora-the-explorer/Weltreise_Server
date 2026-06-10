package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import at.aau.serg.websocketdemoserver.game.minigame.MinigameSubPhase;
import at.aau.serg.websocketdemoserver.game.minigame.MinigameType;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private MinigameType selectedMinigame;
    private MinigameSubPhase minigameSubPhase;
    private String guessQuestionText;
    private Integer guessQuestionAnswer;
    private long guessTimerEndMillis;
    private Map<String, Integer> guessSubmissions = new HashMap<>();
    private Map<String, Long> guessSubmissionTimestamps = new HashMap<>();
    private Integer timerDurationSeconds;
    private int minigameGeneration = 0;

    private List<String> reactionReadyPlayerIds = new ArrayList<>();
    private Long reactionReadyEndsAtMs;
    private Long reactionRoundEndsAtMs;
    private Long reactionStartTimeMs;
    private Long reactionButtonVisibleAtMs;
    private Map<String, Long> reactionPressTimesMs = new HashMap<>();

    public GameRoomState(String lobbyId, String hostId, List<PlayerState> players,
                         GamePhase phase, String currentPlayerId, Integer lastDiceValue, long version) {
        this(lobbyId, hostId, players, phase, currentPlayerId, lastDiceValue, version,
                null,
                new ArrayList<>(), GameMode.CITY_HOPPER, false, null, null, null,
                null, null, null, null, 0L, new HashMap<>(), new HashMap<>(), null, 0,
                new ArrayList<>(), null, null, null, null, new HashMap<>());
    }
}
