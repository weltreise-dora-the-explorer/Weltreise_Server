package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.minigame.*;
import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.Connection;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameOverMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.GoalReachedMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.MinigameLostMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.PlayerScore;
import at.aau.serg.websocketdemoserver.websocket.broker.WebSocketTopics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service für das Verarbeiten von Spieler-Zügen, wie Würfeln und Figur bewegen.
 */
@Service
public class GameCommandService {
    private final Random random;
    private final WorldGraph worldGraph;
    private final MovementEngine movementEngine;
    private final GameSessionService gameSessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CityDistributor cityDistributor;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final GuessQuestionPool guessQuestionPool = new GuessQuestionPool();
    private final FlagQuestionPool flagQuestionPool = new FlagQuestionPool();
    private final QuizQuestionPool quizQuestionPool = new QuizQuestionPool();
    private static final int FLAG_ROUNDS = 5;
    private static final int FLAG_ROUND_SECONDS = 12;
    private static final int FLAG_REVEAL_SECONDS = 3;
    private InMemoryLobbyStore lobbyStore;

    @Autowired(required = false)
    public void setLobbyStore(InMemoryLobbyStore lobbyStore) {
        this.lobbyStore = lobbyStore;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public GameCommandService() {
        this(new Random(), loadWorldGraphSafe(), new MovementEngine(), new GameSessionService(), null, createLoadedCityDistributor());
    }

    public GameCommandService(Random random) {
        this(random, loadWorldGraphSafe(), new MovementEngine(), new GameSessionService(), null, createLoadedCityDistributor());
    }

    @Autowired
    public GameCommandService(WorldGraph worldGraph, MovementEngine movementEngine, GameSessionService gameSessionService,
                              SimpMessagingTemplate messagingTemplate,
                              CityDistributor cityDistributor) {
        this(new Random(), worldGraph, movementEngine, gameSessionService, messagingTemplate, cityDistributor);
    }

    GameCommandService(Random random, WorldGraph worldGraph, MovementEngine movementEngine) {
        this(random, worldGraph, movementEngine, new GameSessionService(), null, createLoadedCityDistributor());
    }

    GameCommandService(Random random,
                       WorldGraph worldGraph,
                       MovementEngine movementEngine,
                       GameSessionService gameSessionService,
                       SimpMessagingTemplate messagingTemplate) {
        this(random, worldGraph, movementEngine, gameSessionService, messagingTemplate, createLoadedCityDistributor());
    }

    GameCommandService(Random random,
                       GameSessionService gameSessionService,
                       SimpMessagingTemplate messagingTemplate) {
        this(random, loadWorldGraphSafe(), new MovementEngine(), gameSessionService, messagingTemplate, createLoadedCityDistributor());
    }

    GameCommandService(Random random,
                       WorldGraph worldGraph,
                       MovementEngine movementEngine,
                       GameSessionService gameSessionService,
                       SimpMessagingTemplate messagingTemplate,
                       CityDistributor cityDistributor) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.worldGraph = Objects.requireNonNull(worldGraph, "worldGraph must not be null");
        this.movementEngine = Objects.requireNonNull(movementEngine, "movementEngine must not be null");
        this.gameSessionService = Objects.requireNonNull(gameSessionService, "gameSessionService must not be null");
        this.messagingTemplate = messagingTemplate;
        this.cityDistributor = Objects.requireNonNull(cityDistributor, "cityDistributor must not be null");
    }

    private static WorldGraph loadWorldGraphSafe() {
        try {
            return WorldGraph.loadFromResources();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load WorldGraph from resources", e);
        }
    }

    private static CityDistributor createLoadedCityDistributor() {
        CityDistributor distributor = new CityDistributor();
        distributor.loadCitiesFromJson();
        return distributor;
    }

    public void processCommand(GameRoomState state, ClientCommand command) {
        validateBase(state, command);

        if(command.getType() == CommandType.UPDATE_GAME_MODE){
            handleUpdateGameMode(state, command);
            return;
        }

        if (command.getType() == CommandType.ROLL_DICE) {
            handleRollDice(state, command);
            return;
        }

        if (command.getType() == CommandType.MOVE_TOKEN) {
            handleMoveToken(state, command);
            return;
        }

        if (command.getType() == CommandType.MOVE_TO_CITY) {
            handleMoveToCity(state, command);
            return;
        }

        if (command.getType() == CommandType.END_TURN) {
            handleEndTurn(state, command);
            return;
        }

        if(command.getType() == CommandType.START_MINIGAME){
            handleStartMinigame(state, command);
            return;
        }

        if(command.getType() == CommandType.ANNOUNCE_MINIGAME_RESULT) {
            handleAnnounceMinigameResult(state, command);
            return;
        }

        if(command.getType() == CommandType.FINISH_MINIGAME){
            handleFinishMinigame(state, command);
            return;
        }

        if(command.getType() == CommandType.REACTION_READY){
            handleReactionReady(state, command);
            return;
        }

        if(command.getType() == CommandType.REACTION_PRESS){
            handleReactionPress(state, command);
            return;
        }

        if(command.getType() == CommandType.USE_FREE_PASS){
            handleUseFreePass(state, command);
            return;
        }

        if(command.getType() == CommandType.USE_SHAKE_CHEAT){
            handleShakeCheat(state, command);
            return;
        }

        if(command.getType() == CommandType.REPORT_CHEAT){
            handleReportCheat(state, command);
            return;
        }

        throw new GameException(ErrorCode.UNSUPPORTED_COMMAND_TYPE, "Unsupported command type for turn flow");
    }

    public void handleSubmitGuess(GameRoomState state, String playerId, int guess) {
        if (state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Command not allowed in current phase");
        }
        if (state.getMinigameSubPhase() != MinigameSubPhase.PLAYING) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Guesses can only be submitted during the playing phase");
        }

        findPlayerState(state.getPlayers(), playerId);

        if (state.getGuessSubmissions().containsKey(playerId)) {
            return;
        }

        boolean flagGame = state.getSelectedMinigame() == MinigameType.FLAG_GAME;
        if (flagGame && (guess < 0 || guess >= state.getFlagOptions().size())) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Option index out of range");
        }

        state.getGuessSubmissions().put(playerId, guess);
        state.getGuessSubmissionTimestamps().put(playerId, System.currentTimeMillis());
        state.setVersion(state.getVersion() + 1);

        if (flagGame) {
            if (allConnectedPlayersAnswered(state)) {
                revealFlagRound(state);
            }
        } else if (state.getSelectedMinigame() == MinigameType.QUIZ_GAME) {
            if(allConnectedPlayersAnswered(state)) {
                evaluateQuizGame(state);
                state.setVersion(state.getVersion() + 1); }
        } else if (state.getGuessSubmissions().size() == state.getPlayers().size()) {
            evaluateGuessGame(state);
            state.setVersion(state.getVersion() + 1);
        }
    }

    private void evaluateGuessGame(GameRoomState state) {
        int answer = state.getGuessQuestionAnswer();

        String winnerId = null;
        int bestDistance = Integer.MAX_VALUE;

        for (PlayerState player : state.getPlayers()) {
            Integer playerGuess = state.getGuessSubmissions().get(player.getPlayerId());
            if (playerGuess == null) continue;
            int distance = Math.abs(playerGuess - answer);
            if (distance < bestDistance) {
                bestDistance = distance;
                winnerId = player.getPlayerId();
            }
        }

        if (winnerId == null) {
            winnerId = state.getCurrentPlayerId();
        }

        state.setMinigameWinnerPlayerId(winnerId);
        state.setMinigameSubPhase(MinigameSubPhase.RESULT);
    }

    private void broadcastState(String lobbyId, GameRoomState state) {
        if (messagingTemplate == null) return;
        messagingTemplate.convertAndSend(
                WebSocketTopics.lobbyEvents(lobbyId),
                new CommandResponse(true, "OK", null, lobbyId, CommandType.START_MINIGAME, state)
        );
    }

    private void handleUpdateGameMode(GameRoomState state, ClientCommand command) {
        if(state.getPhase() != GamePhase.LOBBY) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Gamemode can only be changed in lobby phase");
        }

        if(state.getHostId() == null || !state.getHostId().equals(command.getPlayerId())){
            throw new GameException(ErrorCode.INVALID_COMMAND, "Only the host can change the game mode");
        }

        GameMode selectedGameMode = command.getGameMode();

        if(selectedGameMode == null){
            throw new GameException(ErrorCode.INVALID_COMMAND, "Game mode is required");
        }

        state.setGameMode(selectedGameMode);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleRollDice(GameRoomState state, ClientCommand command) {
        validateTurnContext(state, command);
        if (state.getLastDiceValue() != null) {
            throw new GameException(ErrorCode.DICE_ALREADY_ROLLED, "Dice already rolled for current turn");
        }

        int diceValue = random.nextInt(6) + 1;
        state.setLastDiceValue(diceValue);

        PlayerState currentPlayer = findPlayerState(state.getPlayers(), command.getPlayerId());
        currentPlayer.setRemainingSteps(diceValue);

        // Report-Window schliesst sich bei jedem Wurf, egal welcher Spieler rollt:
        // alle Cheat-Beweise auf null setzen.
        for (PlayerState p : state.getPlayers()) {
            p.setShakeCheatUsedThisRoll(false);
            p.setShakeCheatReported(false);
        }

        recomputeValidMoveIds(state);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleMoveToken(GameRoomState state, ClientCommand command) {
        validateTurnContext(state, command);
        Integer moveSteps = command.getMoveSteps();

        if (state.getLastDiceValue() == null) {
            throw new GameException(ErrorCode.ROLL_REQUIRED_BEFORE_MOVE, "Roll dice before moving");
        }
        if (moveSteps == null) {
            throw new GameException(ErrorCode.MISSING_MOVE_STEPS, "Move steps are required");
        }
        if (!state.getLastDiceValue().equals(moveSteps)) {
            throw new GameException(ErrorCode.INVALID_MOVE_STEPS, "Move steps must match dice value");
        }

        PlayerState playerState = findPlayerState(state.getPlayers(), command.getPlayerId());
        playerState.setBoardPosition(playerState.getBoardPosition() + moveSteps);

        String nextPlayerId = nextPlayerHonoringSkips(state.getPlayers(), state.getCurrentPlayerId());
        state.setCurrentPlayerId(nextPlayerId);
        state.setLastDiceValue(null);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleMoveToCity(GameRoomState state, ClientCommand command) {
        validateTurnContext(state, command);

        if (state.getLastDiceValue() == null) {
            throw new GameException(ErrorCode.ROLL_REQUIRED_BEFORE_MOVE, "Roll dice before moving");
        }

        String targetCityId = command.getTargetCityId();
        if (targetCityId == null) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Target city ID is required");
        }

        PlayerState player = findPlayerState(state.getPlayers(), command.getPlayerId());

        City currentCity = player.getCurrentCity();
        if (currentCity == null) {
            throw new GameException(ErrorCode.CITY_NOT_FOUND, "Player has no current city");
        }

        CityNode currentCityNode = worldGraph.getCityById(currentCity.getId());
        if (currentCityNode == null) {
            throw new GameException(ErrorCode.CITY_NOT_FOUND, "Current city not found in graph");
        }

        CityNode previousCityNode = player.getPreviousCityId() != null
                ? worldGraph.getCityById(player.getPreviousCityId())
                : null;

        CityNode finalDestNode = findFinalDestination(player);

        List<Connection> validOptions = movementEngine.getValidOptions(
                currentCityNode, previousCityNode, player.getRemainingSteps(), finalDestNode);

        Connection chosenConnection = validOptions.stream()
                .filter(conn -> conn.getDestination().getId().equals(targetCityId))
                .findFirst()
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_MOVE_TARGET, "Target city is not a valid move option"));

        player.setPreviousCityId(currentCityNode.getId());

        int newRemainingSteps = player.getRemainingSteps() - chosenConnection.getType().getCost();
        player.setRemainingSteps(newRemainingSteps);

        CityNode targetNode = chosenConnection.getDestination();
        City targetCity = new City(targetNode.getId(), targetNode.getName(), targetNode.getContinent(), targetNode.getColor());
        player.setCurrentCity(targetCity);

        if(isCurrentCityOpenTarget(player)) {
            state.setValidMoveIds(new ArrayList<>());
            
            resetReactionMinigameState(state);
              state.setReactionReadyEndsAtMs(System.currentTimeMillis() + 60_000);
              state.setPhase(GamePhase.MINIGAME);

            state.setVersion(state.getVersion() + 1);
            return;
        }

        if (!state.isGameOver() && gameSessionService.isVictory(player)) {
            state.setGameOver(true);
            broadcastGameOver(state, player.getPlayerId());
        }

        if (newRemainingSteps <= 0) {
            player.setRemainingSteps(0);
            player.setPreviousCityId(null);
            String nextPlayerId = nextPlayerHonoringSkips(state.getPlayers(), state.getCurrentPlayerId());
            state.setCurrentPlayerId(nextPlayerId);
            state.setLastDiceValue(null);
        }

        recomputeValidMoveIds(state);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleEndTurn(GameRoomState state, ClientCommand command) {
        validateTurnContext(state, command);

        if (state.getLastDiceValue() == null) {
            throw new GameException(ErrorCode.ROLL_REQUIRED_BEFORE_MOVE, "Must roll dice before ending turn");
        }

        PlayerState player = findPlayerState(state.getPlayers(), command.getPlayerId());

        if (player.getRemainingSteps() < 0) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "remainingSteps is in an invalid state");
        }

        player.setRemainingSteps(0);
        player.setPreviousCityId(null);

        String nextPlayerId = nextPlayerHonoringSkips(state.getPlayers(), state.getCurrentPlayerId());
        state.setCurrentPlayerId(nextPlayerId);
        state.setLastDiceValue(null);
        recomputeValidMoveIds(state);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleStartMinigame(GameRoomState state, ClientCommand command) {
        if (state.getPhase() != GamePhase.IN_TURN && state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Command not allowed in current phase");
        }
        if (state.getCurrentPlayerId() == null) {
            throw new GameException(ErrorCode.CURRENT_PLAYER_NOT_SET, "Current player is not set");
        }
        if (!state.getCurrentPlayerId().equals(command.getPlayerId())) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "Not your turn");
        }

        if (state.getPhase() == GamePhase.IN_TURN) {
            PlayerState player = findPlayerState(state.getPlayers(), command.getPlayerId());

            if (player.getCurrentCity() == null) {
                throw new GameException(ErrorCode.CITY_NOT_FOUND, "Player has no current city");
            }

            if (!isCurrentCityOpenTarget(player)) {
                throw new GameException(ErrorCode.INVALID_PHASE, "Command not allowed in current phase");
            }

            resetReactionMinigameState(state);
            state.setPhase(GamePhase.MINIGAME);
            state.setVersion(state.getVersion() + 1);
            return;
        }

        MinigameType[] types = {
                MinigameType.GUESS_GAME,
                MinigameType.FLAG_GAME,
                MinigameType.REACTION_GAME,
                MinigameType.QUIZ_GAME
        };
        MinigameType selectedType = types[random.nextInt(types.length)];

        state.getGuessSubmissions().clear();
        state.getGuessSubmissionTimestamps().clear();
        resetReactionMinigameState(state);

        state.setMinigameGeneration(state.getMinigameGeneration() + 1);
        state.setSelectedMinigame(selectedType);
        state.setMinigameSubPhase(MinigameSubPhase.SELECTING);
        state.setGuessTimerEndMillis(0L);

        if (selectedType == MinigameType.FLAG_GAME) {
            startFlagGame(state);
        } else if (selectedType == MinigameType.REACTION_GAME) {
            startReactionGame(state);
        } else if (selectedType == MinigameType.QUIZ_GAME) {
            startQuizGame(state);
        } else {
            startGuessGame(state);
        }

        state.setVersion(state.getVersion() + 1);
    }

    private void startGuessGame(GameRoomState state) {
        GuessQuestion question = guessQuestionPool.getRandom();
        state.setGuessQuestionText(question.getQuestionText());
        state.setGuessQuestionAnswer((int) question.getCorrectAnswer());

        final int generation = state.getMinigameGeneration();
        String lobbyId = state.getLobbyId();

        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() == MinigameSubPhase.SELECTING) {
                state.setMinigameSubPhase(MinigameSubPhase.PLAYING);
                state.setGuessTimerEndMillis(System.currentTimeMillis() + 35_000L);
                state.setTimerDurationSeconds(30);
                state.setVersion(state.getVersion() + 1);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, 6, TimeUnit.SECONDS);

        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() != MinigameSubPhase.RESULT) {
                evaluateGuessGame(state);
                state.setVersion(state.getVersion() + 1);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, 36, TimeUnit.SECONDS);
    }
    private void startQuizGame(GameRoomState state) {
        QuizQuestion question = quizQuestionPool.generateRandomQuestion();

        state.setQuizQuestionText(question.getQuestionText());
        state.setQuizOptions(question.getOptions());
        state.setQuizCorrectAnswerIndex(question.getOptions().indexOf(question.getCorrectAnswer()));

        final int generation = state.getMinigameGeneration();
        String lobbyId = state.getLobbyId();

        //Intro-Phase (6 Sekunden), dann 10 Sekunden Timer starten
        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() == MinigameSubPhase.SELECTING) {
                state.setMinigameSubPhase(MinigameSubPhase.PLAYING);
                state.setGuessTimerEndMillis(System.currentTimeMillis() + 10_000L);
                state.setTimerDurationSeconds(10);
                state.setVersion(state.getVersion() + 1);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, 6, TimeUnit.SECONDS);

        //nach 16 Sekunden (6s Intro + 10s Spielzeit) zwangsweise auflösen
        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() != MinigameSubPhase.RESULT) {
                evaluateQuizGame(state);
                state.setVersion(state.getVersion() + 1);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, 16, TimeUnit.SECONDS);
    }

    private void evaluateQuizGame(GameRoomState state) {
        Integer correctAnswerIndex = state.getQuizCorrectAnswerIndex();
        if (correctAnswerIndex == null) correctAnswerIndex = 0; // Fallback

        String winnerId = null;
        long bestReactionTime = Long.MAX_VALUE;

        for (PlayerState player : state.getPlayers()) {
            String playerId = player.getPlayerId();
            Integer playerGuessIndex = state.getGuessSubmissions().get(playerId);

            if (playerGuessIndex == null) continue;

            if (playerGuessIndex.equals(correctAnswerIndex)) {
                Long submissionTimestamp = state.getGuessSubmissionTimestamps().get(playerId);
                long timerStart = state.getGuessTimerEndMillis() - 10_000L;
                long reactionTime = submissionTimestamp != null ? (submissionTimestamp - timerStart) : Long.MAX_VALUE;

                if (reactionTime < bestReactionTime) {
                    bestReactionTime = reactionTime;
                    winnerId = playerId;
                }
            }
        }

        if (winnerId == null) {
            winnerId = state.getCurrentPlayerId();
        }

        state.setMinigameWinnerPlayerId(winnerId);
        state.setMinigameSubPhase(MinigameSubPhase.RESULT);
    }

    private void startReactionGame(GameRoomState state) {
        final int generation = state.getMinigameGeneration();
        String lobbyId = state.getLobbyId();

        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getSelectedMinigame() == MinigameType.REACTION_GAME
                    && state.getMinigameSubPhase() == MinigameSubPhase.SELECTING) {

                state.setMinigameSubPhase(MinigameSubPhase.PLAYING);
                state.setVersion(state.getVersion() + 1);

                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, 6, TimeUnit.SECONDS);
    }

    private void startFlagGame(GameRoomState state) {
        state.setFlagRounds(flagQuestionPool.generateRounds(FLAG_ROUNDS));
        state.setFlagRoundIndex(0);
        state.getFlagScores().clear();
        state.getFlagTotalTimeMs().clear();
        state.setFlagCode(null);
        state.setFlagOptions(new ArrayList<>());
        state.setFlagCorrectName(null);

        final int generation = state.getMinigameGeneration();
        String lobbyId = state.getLobbyId();

        // Nach dem Auslosungs-Intro (SELECTING) die erste Runde starten.
        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() == MinigameSubPhase.SELECTING) {
                beginFlagRound(state, 0);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, 6, TimeUnit.SECONDS);
    }

    private void beginFlagRound(GameRoomState state, int index) {
        FlagQuestion round = state.getFlagRounds().get(index);
        state.setFlagRoundIndex(index);
        state.setFlagCode(round.getFlagCode());
        state.setFlagOptions(round.getOptions());
        state.setFlagCorrectName(null);                 // erst in ROUND_REVEAL gesetzt
        state.getGuessSubmissions().clear();
        state.getGuessSubmissionTimestamps().clear();
        state.setMinigameSubPhase(MinigameSubPhase.PLAYING);
        state.setGuessTimerEndMillis(System.currentTimeMillis() + (FLAG_ROUND_SECONDS + 2) * 1000L);
        state.setTimerDurationSeconds(FLAG_ROUND_SECONDS);
        state.setVersion(state.getVersion() + 1);

        final int generation = state.getMinigameGeneration();
        final int roundIndex = index;
        String lobbyId = state.getLobbyId();

        // Force-Timer: tippt nicht jeder, schließt der Timer die Runde.
        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() == MinigameSubPhase.PLAYING
                    && state.getFlagRoundIndex() == roundIndex) {
                revealFlagRound(state);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, FLAG_ROUND_SECONDS + 2L, TimeUnit.SECONDS);
    }

    private boolean allConnectedPlayersAnswered(GameRoomState state) {
        long connected = state.getPlayers().stream().filter(PlayerState::isConnected).count();
        return connected > 0 && state.getGuessSubmissions().size() >= connected;
    }

    private void revealFlagRound(GameRoomState state) {
        scoreFlagRound(state);
        FlagQuestion round = state.getFlagRounds().get(state.getFlagRoundIndex());
        state.setFlagCorrectName(round.getCorrectName());      // Auflösung anzeigen
        state.setMinigameSubPhase(MinigameSubPhase.ROUND_REVEAL);
        state.setVersion(state.getVersion() + 1);

        final int generation = state.getMinigameGeneration();
        final int revealedRound = state.getFlagRoundIndex();
        String lobbyId = state.getLobbyId();

        executor.schedule(() -> {
            if (state.getMinigameGeneration() == generation
                    && state.getMinigameSubPhase() == MinigameSubPhase.ROUND_REVEAL
                    && state.getFlagRoundIndex() == revealedRound) {
                advanceFlagRound(state);
                if (lobbyStore != null) lobbyStore.save();
                broadcastState(lobbyId, state);
            }
        }, FLAG_REVEAL_SECONDS, TimeUnit.SECONDS);
    }

    private void advanceFlagRound(GameRoomState state) {
        int next = state.getFlagRoundIndex() + 1;
        if (next < state.getFlagRounds().size()) {
            beginFlagRound(state, next);
        } else {
            finishFlagRounds(state);
        }
    }

    private void finishFlagRounds(GameRoomState state) {
        state.setMinigameWinnerPlayerId(determineFlagWinner(state));
        state.setFlagCorrectName(null);
        state.setMinigameSubPhase(MinigameSubPhase.RESULT);
        state.setGuessTimerEndMillis(0L);
        state.setVersion(state.getVersion() + 1);
    }

    /**
     * Wertet die aktuelle Runde: richtige Antwort -> Punkt + Antwortzeit addieren.
     * Keine/falsche Antwort zählt nicht (und liefert keine Zeit).
     */
    private void scoreFlagRound(GameRoomState state) {
        FlagQuestion round = state.getFlagRounds().get(state.getFlagRoundIndex());
        long roundStart = state.getGuessTimerEndMillis() - (FLAG_ROUND_SECONDS + 2) * 1000L;

        for (PlayerState player : state.getPlayers()) {
            String id = player.getPlayerId();
            Integer choice = state.getGuessSubmissions().get(id);
            if (choice == null) continue;

            boolean correct = choice >= 0 && choice < round.getOptions().size()
                    && round.getOptions().get(choice).equals(round.getCorrectName());
            if (correct) {
                state.getFlagScores().merge(id, 1, Integer::sum);
                Long ts = state.getGuessSubmissionTimestamps().get(id);
                long elapsed = ts != null ? Math.max(0L, ts - roundStart) : 0L;
                state.getFlagTotalTimeMs().merge(id, elapsed, Long::sum);
            }
        }
    }

    /**
     * Gewinner: meiste richtige Antworten; bei Gleichstand kürzere Gesamtzeit;
     * bei exaktem Gleichstand gewinnt der Stadteroberer (verteidigt).
     */
    String determineFlagWinner(GameRoomState state) {
        int bestScore = -1;
        long bestTime = Long.MAX_VALUE;

        for (PlayerState player : state.getPlayers()) {
            int score = flagScoreOf(state, player.getPlayerId());
            long time = flagTimeOf(state, player.getPlayerId());
            if (score > bestScore || (score == bestScore && time < bestTime)) {
                bestScore = score;
                bestTime = time;
            }
        }

        String conqueror = state.getCurrentPlayerId();
        if (isFlagBest(state, conqueror, bestScore, bestTime)) {
            return conqueror;
        }
        for (PlayerState player : state.getPlayers()) {
            if (isFlagBest(state, player.getPlayerId(), bestScore, bestTime)) {
                return player.getPlayerId();
            }
        }
        return conqueror;
    }

    private boolean isFlagBest(GameRoomState state, String id, int bestScore, long bestTime) {
        return id != null
                && flagScoreOf(state, id) == bestScore
                && flagTimeOf(state, id) == bestTime;
    }

    private int flagScoreOf(GameRoomState state, String id) {
        return state.getFlagScores().getOrDefault(id, 0);
    }

    private long flagTimeOf(GameRoomState state, String id) {
        // Ohne richtige Antwort zählt die Zeit als "schlechteste" (MAX).
        return flagScoreOf(state, id) > 0
                ? state.getFlagTotalTimeMs().getOrDefault(id, Long.MAX_VALUE)
                : Long.MAX_VALUE;
    }

    private void handleFinishMinigame(GameRoomState state, ClientCommand command) {
        if(state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Minigame can only be finished during minigame phase");
        }

        if(state.getCurrentPlayerId() == null) {
            throw new GameException(ErrorCode.CURRENT_PLAYER_NOT_SET, "Current player is not set");
        }

        if(!state.getCurrentPlayerId().equals(command.getPlayerId())) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "Only the current target player can finish the minigame prototype");
        }

        PlayerState targetPlayer = findPlayerState(state.getPlayers(), command.getPlayerId());

        if(targetPlayer.getCurrentCity() == null) {
            throw new GameException(ErrorCode.CITY_NOT_FOUND, "Player has no current city");
        }

        boolean isTargetCity = targetPlayer.getOwnedCities().stream()
                .anyMatch(city -> city.getId().equals(targetPlayer.getCurrentCity().getId()));

        if(!isTargetCity) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Current city is not a target city");
        }

        boolean alreadyCompleted = targetPlayer.getVisitedCities().stream()
                .anyMatch(city -> city.getId().equals(targetPlayer.getCurrentCity().getId()));

        if(alreadyCompleted) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Target city is already completed");
        }

        String winnerPlayerId = command.getWinnerPlayerId();

        if(winnerPlayerId == null) {
            winnerPlayerId = state.getMinigameWinnerPlayerId();
        }

        if(winnerPlayerId == null) {
            winnerPlayerId = command.getPlayerId();
        }

        PlayerState winner = findPlayerState(state.getPlayers(), winnerPlayerId);

        if(winner.getPlayerId().equals(targetPlayer.getPlayerId())) {
            targetPlayer.getVisitedCities().add(targetPlayer.getCurrentCity());
            broadcastGoalReached(targetPlayer, targetPlayer.getCurrentCity());

            if (!state.isGameOver() && gameSessionService.isVictory(targetPlayer)) {
                state.setGameOver(true);
                broadcastGameOver(state, targetPlayer.getPlayerId());
            }
        } else {
            winner.setFreePassCount(winner.getFreePassCount() + 1);
            replaceCurrentTargetCity(state, targetPlayer);
            sendMinigameLost(state.getLobbyId(), targetPlayer.getPlayerId(),
                    state.getMinigameLostCityName(), state.getMinigameNewCityName());
            state.setMinigameLostCityName(null);
            state.setMinigameNewCityName(null);
        }

        if(targetPlayer.getRemainingSteps() <= 0) {
            targetPlayer.setRemainingSteps(0);
            targetPlayer.setPreviousCityId(null);
            String nextPlayerId = nextPlayerHonoringSkips(state.getPlayers(), state.getCurrentPlayerId());
            state.setCurrentPlayerId(nextPlayerId);
            state.setLastDiceValue(null);
            state.setValidMoveIds(new ArrayList<>());
        } else {
            recomputeValidMoveIds(state);
        }

        state.setMinigameWinnerPlayerId(null);
        state.setMinigameSubPhase(null);
        state.setSelectedMinigame(null);
        state.setGuessQuestionText(null);
        state.setGuessQuestionAnswer(null);
        state.setGuessTimerEndMillis(0L);
        state.setTimerDurationSeconds(null);
        state.getGuessSubmissions().clear();
        state.getGuessSubmissionTimestamps().clear();
        state.setPhase(GamePhase.IN_TURN);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleAnnounceMinigameResult(GameRoomState state, ClientCommand command) {
        if(state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Minigame result can only be announced during minigame phase");
        }

        if(state.getCurrentPlayerId() == null) {
            throw new GameException(ErrorCode.CURRENT_PLAYER_NOT_SET, "Current player is not set");
        }

        if(!state.getCurrentPlayerId().equals(command.getPlayerId())) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "Only the current target player can announce the minigame result");
        }

        String winnerPlayerId = command.getWinnerPlayerId();

        if(winnerPlayerId == null) {
            winnerPlayerId = command.getPlayerId();
        }

        findPlayerState(state.getPlayers(), winnerPlayerId);

        state.setMinigameWinnerPlayerId(winnerPlayerId);
        state.setVersion(state.getVersion() + 1);
    }

    private boolean isCurrentCityOpenTarget(PlayerState player) {
        if(player.getCurrentCity() == null) {
            return false;
        }

        boolean isTargetCity = player.getOwnedCities().stream()
                .anyMatch(city -> city.getId().equals(player.getCurrentCity().getId()));

        boolean alreadyCompleted = player.getVisitedCities().stream()
                .anyMatch(city -> city.getId().equals(player.getCurrentCity().getId()));

        return isTargetCity && !alreadyCompleted;
    }

    private void handleUseFreePass(GameRoomState state, ClientCommand command) {
        if (state.isGameOver()) {
            throw new GameException(ErrorCode.GAME_OVER, "Das Spiel ist bereits beendet");
        }
        if (state.getPhase() != GamePhase.IN_TURN && state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Command not allowed in current phase");
        }
        if (state.getCurrentPlayerId() == null) {
            throw new GameException(ErrorCode.CURRENT_PLAYER_NOT_SET, "Current player is not set");
        }
        if (!state.getCurrentPlayerId().equals(command.getPlayerId())) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "Not your turn");
        }

        PlayerState player = findPlayerState(state.getPlayers(), command.getPlayerId());

        if(player.getFreePassCount() <= 0) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Player has no free pass available");
        }

        if(player.getCurrentCity() == null){
            throw new GameException(ErrorCode.CITY_NOT_FOUND, "Player has no current city");
        }

        if(!isCurrentCityOpenTarget(player)) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Free pass can only be used on an open target city");
        }

        player.setFreePassCount(player.getFreePassCount() - 1);
        player.getVisitedCities().add(player.getCurrentCity());
        broadcastGoalReached(player, player.getCurrentCity());

        if(!state.isGameOver() && gameSessionService.isVictory(player)) {
            state.setGameOver(true);
            broadcastGameOver(state, player.getPlayerId());
        }

        state.setPhase(GamePhase.IN_TURN);
        state.setMinigameSubPhase(null);
        state.setSelectedMinigame(null);

        if(player.getRemainingSteps() <= 0){
            player.setRemainingSteps(0);
            player.setPreviousCityId(null);
            String nextPlayerId = nextPlayerHonoringSkips(state.getPlayers(), state.getCurrentPlayerId());
            state.setCurrentPlayerId(nextPlayerId);
            state.setLastDiceValue(null);
            state.setValidMoveIds(new ArrayList<>());
        } else {
            recomputeValidMoveIds(state);
        }

        state.setVersion(state.getVersion() + 1);
    }

    private void handleShakeCheat(GameRoomState state, ClientCommand command) {
        validateTurnContext(state, command);

        PlayerState player = findPlayerState(state.getPlayers(), command.getPlayerId());

        if (player.getRemainingSteps() != 1) {
            throw new GameException(ErrorCode.SHAKE_CHEAT_NOT_ALLOWED,
                    "Shake cheat only allowed with exactly 1 remaining step");
        }

        if (player.isShakeCheatUsedThisRoll()) {
            throw new GameException(ErrorCode.SHAKE_CHEAT_NOT_ALLOWED,
                    "Shake cheat already used in this dice roll");
        }

        player.setShakeCheatUsedThisRoll(true);
        player.setRemainingSteps(2);

        recomputeValidMoveIds(state);
        state.setVersion(state.getVersion() + 1);
    }

    private void handleReportCheat(GameRoomState state, ClientCommand command) {
        if (state.isGameOver()) {
            throw new GameException(ErrorCode.REPORT_NOT_ALLOWED, "Game already over");
        }
        if (state.getPhase() == GamePhase.LOBBY) {
            throw new GameException(ErrorCode.REPORT_NOT_ALLOWED, "Reports only allowed during active game");
        }

        String reportedPlayerId = command.getReportedPlayerId();
        if (reportedPlayerId == null || reportedPlayerId.isBlank()) {
            throw new GameException(ErrorCode.REPORT_NOT_ALLOWED, "reportedPlayerId is required");
        }
        if (reportedPlayerId.equals(command.getPlayerId())) {
            throw new GameException(ErrorCode.REPORT_NOT_ALLOWED, "Cannot report yourself");
        }

        PlayerState reporter = findPlayerState(state.getPlayers(), command.getPlayerId());
        PlayerState reported = findPlayerState(state.getPlayers(), reportedPlayerId);

        boolean hit = reported.isShakeCheatUsedThisRoll() && !reported.isShakeCheatReported();
        if (hit) {
            reported.setShakeCheatReported(true);
            reported.setMustSkipNextTurn(true);
        } else if (command.getPlayerId().equals(state.getCurrentPlayerId())) {
            // Falschmeldung waehrend des eigenen Zugs: der Melder verliert sofort
            // den aktuellen Zug, statt erst die naechste Runde ausgesetzt zu werden.
            reporter.setRemainingSteps(0);
            reporter.setPreviousCityId(null);
            String nextPlayerId = nextPlayerHonoringSkips(state.getPlayers(), state.getCurrentPlayerId());
            state.setCurrentPlayerId(nextPlayerId);
            state.setLastDiceValue(null);
            recomputeValidMoveIds(state);
        } else {
            // Falschmeldung ausserhalb des eigenen Zugs: naechster Zug wird ausgesetzt.
            reporter.setMustSkipNextTurn(true);
        }

        state.setVersion(state.getVersion() + 1);
    }

    private void sendMinigameLost(String lobbyId, String playerId, String lostCityName, String newCityName) {
        if (messagingTemplate == null || lobbyId == null || playerId == null) return;
        messagingTemplate.convertAndSend(
                WebSocketTopics.playerEvents(lobbyId, playerId),
                new MinigameLostMessage(lostCityName, newCityName)
        );
    }

    private void broadcastGoalReached(PlayerState player, City city) {
        if (messagingTemplate == null) return;

        GoalReachedMessage message = new GoalReachedMessage(
                player.getPlayerId(),
                city.getName(),
                player.getVisitedCities().size(),
                player.getOwnedCities().size()
        );

        messagingTemplate.convertAndSend(WebSocketTopics.GOAL_REACHED, message);
    }

    private void broadcastGameOver(GameRoomState state, String winnerId) {
        if (messagingTemplate == null) return;

        List<PlayerScore> scores = state.getPlayers().stream()
                .map(player -> new PlayerScore(player.getPlayerId(), calculateScore(player)))
                .collect(Collectors.toList());

        messagingTemplate.convertAndSend(WebSocketTopics.GAME_OVER, new GameOverMessage(winnerId, scores));
    }

    int calculateScore(PlayerState player) {
        return player.getVisitedCities().size();
    }

    private void replaceCurrentTargetCity(GameRoomState state, PlayerState targetPlayer) {
        City lostCity = targetPlayer.getCurrentCity();

        if(lostCity == null) {
            throw new GameException(ErrorCode.CITY_NOT_FOUND, "Player has no current city");
        }

        state.setMinigameLostCityName(lostCity.getName());

        targetPlayer.getOwnedCities().removeIf(city -> city.getId().equals(lostCity.getId()));

        List<City> candidates = cityDistributor.getAllCities().stream()
                .filter(city -> !city.getId().equals(lostCity.getId()))
                .filter(city -> !containsCityById(targetPlayer.getOwnedCities(), city.getId()))
                .filter(city -> !containsCityById(targetPlayer.getVisitedCities(), city.getId()))
                .filter(city -> !isCityAssignedToAnyPlayer(state.getPlayers(), city.getId()))
                .filter(city -> !isStartCityOfAnyPlayer(state.getPlayers(), city.getId()))
                .toList();

        if (candidates.isEmpty()) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "No replacement city available");
        }

        // Zufällige Ersatzstadt statt immer der ersten aus der Liste.
        City replacementCity = candidates.get(random.nextInt(candidates.size()));

        targetPlayer.getOwnedCities().add(replacementCity);
        state.setMinigameNewCityName(replacementCity.getName());
    }

    private boolean isStartCityOfAnyPlayer(List<PlayerState> players, String cityId) {
        return players.stream()
                .anyMatch(p -> p.getStartCity() != null
                        && p.getStartCity().getId().equals(cityId));
    }

    private boolean containsCityById(List<City> cities, String cityId) {
        return cities.stream().anyMatch(city -> city.getId().equals(cityId));
    }

    private boolean isCityAssignedToAnyPlayer(List<PlayerState> players, String cityId) {
        return players.stream()
                .anyMatch(player -> containsCityById(player.getOwnedCities(), cityId));
    }

    private void handleReactionReady(GameRoomState state, ClientCommand command) {
        if(state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Reaction ready is only allowed during minigame phase");
        }

        findPlayerState(state.getPlayers(), command.getPlayerId());

        if(!state.getReactionReadyPlayerIds().contains(command.getPlayerId())) {
            state.getReactionReadyPlayerIds().add(command.getPlayerId());
        }

        boolean allPlayersReady = state.getPlayers().stream()
                .allMatch(player -> state.getReactionReadyPlayerIds().contains(player.getPlayerId()));

        if(state.getReactionReadyEndsAtMs() == null) {
            state.setReactionReadyEndsAtMs(System.currentTimeMillis() + 60_000);
        }

        long now = System.currentTimeMillis();
        startReactionRoundIfReadyTimedOut(state, now);

        if(allPlayersReady && state.getReactionStartTimeMs() == null) {
            long countdownStartTimeMs = System.currentTimeMillis();
            long randomWaitTimeMs = 500 + random.nextInt(4501);

            state.setReactionStartTimeMs(countdownStartTimeMs);

            long buttonVisibleAtMs =
                    countdownStartTimeMs + 3000 + randomWaitTimeMs;

            state.setReactionButtonVisibleAtMs(buttonVisibleAtMs);

            state.setReactionRoundEndsAtMs(
                    buttonVisibleAtMs + 60_000
            );
        }

        state.setVersion(state.getVersion() + 1);
    }

    private void handleReactionPress(GameRoomState state, ClientCommand command) {
        if(state.getPhase() != GamePhase.MINIGAME) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Reaction press is only allowed during minigame phase");
        }

        findPlayerState(state.getPlayers(), command.getPlayerId());

        if(state.getReactionButtonVisibleAtMs() == null) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Reaction button is not available yet");
        }

        long now = System.currentTimeMillis();

        finishReactionRoundIfTimedOut(state, now);

        if(state.getMinigameWinnerPlayerId() != null) {
            state.setVersion(state.getVersion() + 1);
            return;
        }

        long visibleAtMs = state.getReactionButtonVisibleAtMs();
        long earlyToleranceMs = 300L;

        if (now + earlyToleranceMs < visibleAtMs) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Reaction button was pressed too early");
        }

        if (state.getReactionPressTimesMs().containsKey(command.getPlayerId())) {
            return;
        }

        long reactionTimeMs = Math.max(0L, now - visibleAtMs);
        state.getReactionPressTimesMs().put(command.getPlayerId(), reactionTimeMs);

        boolean allPlayersPressed = state.getPlayers().stream()
                .allMatch(player -> state.getReactionPressTimesMs().containsKey(player.getPlayerId()));

        if(allPlayersPressed) {
            String winnerPlayerId = state.getReactionPressTimesMs().entrySet().stream()
                    .min(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse(command.getPlayerId());

            state.setMinigameWinnerPlayerId(winnerPlayerId);
        }

        state.setVersion(state.getVersion() + 1);
    }

    private void resetReactionMinigameState(GameRoomState state) {
        state.getReactionReadyPlayerIds().clear();
        state.setReactionReadyEndsAtMs(null);
        state.setReactionStartTimeMs(null);
        state.setReactionButtonVisibleAtMs(null);
        state.setReactionRoundEndsAtMs(null);
        state.getReactionPressTimesMs().clear();
        state.setMinigameWinnerPlayerId(null);
    }

    private void finishReactionRoundIfTimedOut(GameRoomState state, long now) {
        if(state.getReactionRoundEndsAtMs() == null) {
            return;
        }

        if(now < state.getReactionRoundEndsAtMs()) {
            return;
        }

        for(PlayerState player : state.getPlayers()) {
            state.getReactionPressTimesMs().putIfAbsent(
                    player.getPlayerId(),
                    60_000L
            );
        }

        if(state.getMinigameWinnerPlayerId() == null) {
            String winnerPlayerId = state.getReactionPressTimesMs().entrySet().stream()
                    .min(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse(state.getCurrentPlayerId());

            state.setMinigameWinnerPlayerId(winnerPlayerId);
        }
    }

    private void startReactionRoundIfReadyTimedOut(GameRoomState state, long now) {
        if(state.getReactionReadyEndsAtMs() == null) {
            return;
        }

        if(state.getReactionStartTimeMs() != null) {
            return;
        }

        if(now < state.getReactionReadyEndsAtMs()) {
            return;
        }

        long randomWaitTimeMs = 500 + random.nextInt(4501);

        state.setReactionStartTimeMs(now);

        long buttonVisibleAtMs = now + 3000 + randomWaitTimeMs;
        state.setReactionButtonVisibleAtMs(buttonVisibleAtMs);
        state.setReactionRoundEndsAtMs(buttonVisibleAtMs + 60_000);
    }

    private void recomputeValidMoveIds(GameRoomState state) {
        if (state.getLastDiceValue() == null) {
            state.setValidMoveIds(new ArrayList<>());
            return;
        }

        PlayerState player = state.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(state.getCurrentPlayerId()))
                .findFirst()
                .orElse(null);

        if (player == null || player.getRemainingSteps() <= 0 || player.getCurrentCity() == null) {
            state.setValidMoveIds(new ArrayList<>());
            return;
        }

        CityNode currentNode = worldGraph.getCityById(player.getCurrentCity().getId());
        if (currentNode == null) {
            state.setValidMoveIds(new ArrayList<>());
            return;
        }

        CityNode previousNode = player.getPreviousCityId() != null
                ? worldGraph.getCityById(player.getPreviousCityId())
                : null;

        List<String> ids = movementEngine
                .getValidOptions(currentNode, previousNode, player.getRemainingSteps(), findFinalDestination(player))
                .stream()
                .map(conn -> conn.getDestination().getId())
                .collect(Collectors.toList());

        state.setValidMoveIds(ids);
    }

    private CityNode findFinalDestination(PlayerState player) {
        List<City> owned = player.getOwnedCities();
        List<City> visited = player.getVisitedCities();
        if (owned == null || owned.isEmpty()) return null;

        return owned.stream()
                .filter(city -> visited.stream().noneMatch(v -> v.getId().equals(city.getId())))
                .findFirst()
                .map(city -> worldGraph.getCityById(city.getId()))
                .orElse(null);
    }

    private void validateBase(GameRoomState state, ClientCommand command) {
        if (state == null || command == null) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "State and command are required");
        }
        if (command.getType() == null || command.getPlayerId() == null) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "Command type and playerId are required");
        }
    }

    private void validateTurnContext(GameRoomState state, ClientCommand command) {
        if (state.isGameOver()) {
            throw new GameException(ErrorCode.GAME_OVER, "Das Spiel ist bereits beendet");
        }

        if (state.getPhase() != GamePhase.IN_TURN) {
            throw new GameException(ErrorCode.INVALID_PHASE, "Command not allowed in current phase");
        }
        if (state.getCurrentPlayerId() == null) {
            throw new GameException(ErrorCode.CURRENT_PLAYER_NOT_SET, "Current player is not set");
        }
        if (!state.getCurrentPlayerId().equals(command.getPlayerId())) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "Not your turn");
        }
    }

    private PlayerState findPlayerState(List<PlayerState> players, String playerId) {
        return players.stream()
                .filter(player -> playerId.equals(player.getPlayerId()))
                .findFirst()
                .orElseThrow(() -> new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Player is not in lobby"));
    }

    private String nextPlayerId(List<PlayerState> players, String currentPlayerId) {
        if (players == null || players.isEmpty()) {
            throw new GameException(ErrorCode.INVALID_COMMAND, "At least one player is required");
        }

        int currentIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (currentPlayerId.equals(players.get(i).getPlayerId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) {
            throw new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Current player is not in lobby");
        }

        int nextIndex = (currentIndex + 1) % players.size();
        return players.get(nextIndex).getPlayerId();
    }

    /**
     * Wie {@link #nextPlayerId}, ueberspringt aber Spieler mit mustSkipNextTurn.
     * Das Flag wird konsumiert (auf false gesetzt), sobald der Spieler uebersprungen
     * wurde. Safety-Counter verhindert eine Endlosschleife, falls alle Spieler
     * gleichzeitig skip-markiert sind.
     */
    private String nextPlayerHonoringSkips(List<PlayerState> players, String currentPlayerId) {
        String next = nextPlayerId(players, currentPlayerId);
        int safety = players.size();
        while (safety-- > 0) {
            PlayerState candidate = findPlayerState(players, next);
            if (!candidate.isMustSkipNextTurn()) {
                return next;
            }
            candidate.setMustSkipNextTurn(false);
            next = nextPlayerId(players, next);
        }
        return next;
    }
}