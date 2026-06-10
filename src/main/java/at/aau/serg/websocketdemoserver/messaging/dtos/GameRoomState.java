package at.aau.serg.websocketdemoserver.messaging.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import at.aau.serg.websocketdemoserver.game.minigame.FlagQuestion;
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

    // Flaggenspiel (FLAG_GAME) – pro Runde gebroadcastet
    private int flagRoundIndex = 0;
    private String flagCode;
    private List<String> flagOptions = new ArrayList<>();
    private String flagCorrectName;                       // nur in ROUND_REVEAL gesetzt
    private Map<String, Integer> flagScores = new HashMap<>();
    private Map<String, Long> flagTotalTimeMs = new HashMap<>();

    // Antwort-Schlüssel der 5 Runden – serverseitig, NICHT im Broadcast/Persist
    @JsonIgnore
    private List<FlagQuestion> flagRounds = new ArrayList<>();

    public GameRoomState(String lobbyId, String hostId, List<PlayerState> players,
                         GamePhase phase, String currentPlayerId, Integer lastDiceValue, long version) {
        this(lobbyId, hostId, players, phase, currentPlayerId, lastDiceValue, version,
                new ArrayList<>(), GameMode.CITY_HOPPER, false, null, null, null,
                null, null, null, null, 0L, new HashMap<>(), new HashMap<>(), null, 0,
                0, null, new ArrayList<>(), null, new HashMap<>(), new HashMap<>(), new ArrayList<>());
    }
}
