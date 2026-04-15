package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
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
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-2", null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Not your turn");
    }

    @Test
    void moveTokenRejectsWhenDiceWasNotRolled() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 3)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Roll dice before moving");
    }

    @Test
    void moveTokenRejectsWhenStepsDoNotMatchDiceValue() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 2)))
                .isInstanceOf(GameException.class)
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
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-2", 2)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Not your turn");
    }

    @Test
    void rollDiceRejectsWhenDiceAlreadyRolled() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Dice already rolled");
    }

    @Test
    void processCommandRejectsNullCommand() {
        GameCommandService service = new GameCommandService();

        assertThatThrownBy(() -> service.processCommand(new GameRoomState(), null))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("required");
    }

    @Test
    void processCommandRejectsNullState() {
        GameCommandService service = new GameCommandService();
        ClientCommand command = new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null);

        assertThatThrownBy(() -> service.processCommand(null, command))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("required");
    }

    @Test
    void processCommandRejectsWrongPhase() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = new GameRoomState();
        state.setPhase(GamePhase.LOBBY);
        state.setPlayers(players("player-1", "player-2"));
        state.setCurrentPlayerId("player-1");

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("current phase");
    }

    @Test
    void moveTokenRejectsWhenMoveStepsNull() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState("player-1", players("player-1", "player-2"));
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Move steps are required");
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
        PlayerState p1 = new PlayerState(firstPlayerId);
        p1.setCurrentCity(new City("vienna", "Vienna", Continent.EUROPE, CityColor.RED));
        players.add(p1);
        PlayerState p2 = new PlayerState(secondPlayerId);
        p2.setCurrentCity(new City("paris", "Paris", Continent.EUROPE, CityColor.BLUE));
        players.add(p2);
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
