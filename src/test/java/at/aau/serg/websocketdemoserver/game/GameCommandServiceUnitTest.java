package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.messaging.dtos.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameCommandServiceUnitTest {

    @Test
    void rollDiceSetsDiceValueAndIncrementsVersion() {
        GameCommandService service = new GameCommandService(new FixedRandom(4));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));

        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        assertThat(state.getLastDiceValue()).isEqualTo(5);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void rollDiceRejectsNonActivePlayer() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-2", null)
        )).isInstanceOf(GameException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_YOUR_TURN);
        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-2", null)
        ))
                .hasMessageContaining("Not your turn");
    }

    @Test
    void moveTokenRejectsWhenDiceWasNotRolled() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 3)
        )).isInstanceOf(GameException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROLL_REQUIRED_BEFORE_MOVE);
        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 3)
        ))
                .hasMessageContaining("Roll dice before moving");
    }

    @Test
    void moveTokenRejectsWhenStepsDoNotMatchDiceValue() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 2)
        )).isInstanceOf(GameException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_MOVE_STEPS);
        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 2)
        ))
                .hasMessageContaining("Move steps must match dice value");
    }

    @Test
    void moveTokenAdvancesPositionRotatesTurnAndClearsDice() {
        GameCommandService service = new GameCommandService(new FixedRandom(2));
        List<PlayerState> players = players("player-1", "player-2");
        GameRoomState state = inTurnState("player-1", players);
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        service.processCommand(state, new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 3));

        assertThat(players.get(0).getBoardPosition()).isEqualTo(3);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(state.getLastDiceValue()).isNull();
        assertThat(state.getVersion()).isEqualTo(2L);
    }

    @Test
    void moveTokenRejectsNonActivePlayer() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-2", 2)
        )).isInstanceOf(GameException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_YOUR_TURN);
        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-2", 2)
        ))
                .hasMessageContaining("Not your turn");
    }

    private GameRoomState inTurnState(String currentPlayerId, List<PlayerState> players) {
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setPlayers(players);
        state.setPhase(GamePhase.IN_TURN);
        state.setCurrentPlayerId(currentPlayerId);
        return state;
    }

    private List<PlayerState> players(String firstPlayerId, String secondPlayerId) {
        List<PlayerState> players = new ArrayList<>();
        players.add(new PlayerState(firstPlayerId, "Vienna", 0));
        players.add(new PlayerState(secondPlayerId, "Paris", 0));
        return players;
    }

    private static class FixedRandom extends Random {
        private final int value;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
