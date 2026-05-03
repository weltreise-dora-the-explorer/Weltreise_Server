package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.Connection;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Service für das Verarbeiten von Spieler-Zügen, wie Würfeln und Figur bewegen.
 */
@Service
public class GameCommandService {
    private final Random random;
    private final WorldGraph worldGraph;
    private final MovementEngine movementEngine;

    public GameCommandService() {
        this(new Random(), loadWorldGraphSafe(), new MovementEngine());
    }

    public GameCommandService(Random random) {
        this(random, loadWorldGraphSafe(), new MovementEngine());
    }

    @Autowired
    public GameCommandService(WorldGraph worldGraph, MovementEngine movementEngine) {
        this(new Random(), worldGraph, movementEngine);
    }

    GameCommandService(Random random, WorldGraph worldGraph, MovementEngine movementEngine) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.worldGraph = Objects.requireNonNull(worldGraph, "worldGraph must not be null");
        this.movementEngine = Objects.requireNonNull(movementEngine, "movementEngine must not be null");
    }

    private static WorldGraph loadWorldGraphSafe() {
        try {
            return WorldGraph.loadFromResources();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load WorldGraph from resources", e);
        }
    }

    public void processCommand(GameRoomState state, ClientCommand command) {
        validateBase(state, command);
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

        throw new GameException(ErrorCode.UNSUPPORTED_COMMAND_TYPE, "Unsupported command type for turn flow");
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

        String nextPlayerId = nextPlayerId(state.getPlayers(), state.getCurrentPlayerId());
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
        player.setCurrentCity(new City(targetNode.getId(), targetNode.getName(), targetNode.getContinent(), targetNode.getColor()));

        if (newRemainingSteps <= 0) {
            player.setRemainingSteps(0);
            String nextPlayerId = nextPlayerId(state.getPlayers(), state.getCurrentPlayerId());
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

        String nextPlayerId = nextPlayerId(state.getPlayers(), state.getCurrentPlayerId());
        state.setCurrentPlayerId(nextPlayerId);
        state.setLastDiceValue(null);
        state.setVersion(state.getVersion() + 1);
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
}
