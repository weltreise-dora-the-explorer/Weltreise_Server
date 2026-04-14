package at.aau.serg.websocketdemoserver.messaging.dtos;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurnBasedDtosUnitTest {

    @Test
    void commandTypeContainsExpectedValues() {
        assertThat(CommandType.values()).containsExactly(
                CommandType.CREATE_LOBBY,
                CommandType.JOIN_LOBBY,
                CommandType.START_GAME,
                CommandType.ROLL_DICE,
                CommandType.MOVE_TOKEN,
                CommandType.LEAVE_LOBBY
        );
    }

    @Test
    void gamePhaseContainsExpectedValues() {
        assertThat(GamePhase.values()).containsExactly(
                GamePhase.LOBBY,
                GamePhase.CITY_ASSIGNMENT,
                GamePhase.IN_TURN
        );
    }

    @Test
    void clientCommandStoresProvidedValues() {
        ClientCommand command = new ClientCommand(
                CommandType.MOVE_TOKEN,
                "lobby-1",
                "player-1",
                4
        );

        assertThat(command.getType()).isEqualTo(CommandType.MOVE_TOKEN);
        assertThat(command.getLobbyId()).isEqualTo("lobby-1");
        assertThat(command.getPlayerId()).isEqualTo("player-1");
        assertThat(command.getMoveSteps()).isEqualTo(4);
    }

    @Test
    void playerStateStoresProvidedValues() {
        City vienna = new City("vienna", "Vienna", Continent.EUROPE, CityColor.RED);
        PlayerState playerState = new PlayerState("player-1");
        playerState.setCurrentCity(vienna);
        playerState.setBoardPosition(12);

        assertThat(playerState.getPlayerId()).isEqualTo("player-1");
        assertThat(playerState.getCurrentCity()).isEqualTo(vienna);
        assertThat(playerState.getBoardPosition()).isEqualTo(12);
    }

    @Test
    void gameRoomStateStoresProvidedValues() {
        List<PlayerState> players = new ArrayList<>();
        PlayerState p1 = new PlayerState("player-1");
        p1.setCurrentCity(new City("vienna", "Vienna", Continent.EUROPE, CityColor.RED));
        p1.setBoardPosition(2);
        players.add(p1);

        GameRoomState roomState = new GameRoomState(
                "lobby-1",
                players,
                GamePhase.IN_TURN,
                "player-1",
                6,
                3L
        );

        assertThat(roomState.getLobbyId()).isEqualTo("lobby-1");
        assertThat(roomState.getPlayers()).hasSize(1);
        assertThat(roomState.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(roomState.getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(roomState.getLastDiceValue()).isEqualTo(6);
        assertThat(roomState.getVersion()).isEqualTo(3L);
    }

    @Test
    void gameRoomStateDefaultValuesMatchTurnSetupNeeds() {
        GameRoomState roomState = new GameRoomState();

        assertThat(roomState.getPlayers()).isNotNull();
        assertThat(roomState.getPlayers()).isEmpty();
        assertThat(roomState.getPhase()).isEqualTo(GamePhase.LOBBY);
        assertThat(roomState.getVersion()).isEqualTo(0L);
    }
}
