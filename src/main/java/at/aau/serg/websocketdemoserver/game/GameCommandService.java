package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Service für das Verarbeiten von Spieler-Zügen, wie Würfeln und Figur bewegen.
 */
@Service
public class GameCommandService {
    private final Random random;

    public GameCommandService() {
        this(new Random());
    }

    public GameCommandService(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
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

        throw new GameException(ErrorCode.UNSUPPORTED_COMMAND_TYPE, "Unsupported command type for turn flow");
    }

    private void handleRollDice(GameRoomState state, ClientCommand command) {
        validateTurnContext(state, command);
        if (state.getLastDiceValue() != null) {
            throw new GameException(ErrorCode.DICE_ALREADY_ROLLED, "Dice already rolled for current turn");
        }

        state.setLastDiceValue(random.nextInt(6) + 1);
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
