package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameCommandService;
import at.aau.serg.websocketdemoserver.game.InMemoryLobbyStore;
import at.aau.serg.websocketdemoserver.game.LobbyService;
import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketBrokerControllerUnitTest {

    @Mock
    private LobbyService lobbyService;
    @Mock
    private GameCommandService gameCommandService;
    @Mock
    private InMemoryLobbyStore lobbyStore;

    @Test
    void handleLobbyCommandRoutesJoinAndReturnsSuccessResponse() {
        WebSocketBrokerController controller = new WebSocketBrokerController(lobbyService, gameCommandService, lobbyStore);
        ClientCommand command = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-1", null);
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        when(lobbyService.joinLobby("lobby-1", "player-1")).thenReturn(state);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getLobbyId()).isEqualTo("lobby-1");
        assertThat(response.getCommandType()).isEqualTo(CommandType.JOIN_LOBBY);
        assertThat(response.getState()).isEqualTo(state);
        verify(lobbyService).joinLobby("lobby-1", "player-1");
    }

    @Test
    void handleLobbyCommandRoutesRollDiceThroughGameService() {
        WebSocketBrokerController controller = new WebSocketBrokerController(lobbyService, gameCommandService, lobbyStore);
        ClientCommand command = new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null);
        GameRoomState state = new GameRoomState();
        when(lobbyStore.get("lobby-1")).thenReturn(Optional.of(state));

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCommandType()).isEqualTo(CommandType.ROLL_DICE);
        assertThat(response.getState()).isEqualTo(state);
        verify(gameCommandService).processCommand(state, command);
    }

    @Test
    void handleLobbyCommandReturnsErrorResponseOnValidationFailure() {
        WebSocketBrokerController controller = new WebSocketBrokerController(lobbyService, gameCommandService, lobbyStore);
        ClientCommand command = new ClientCommand(CommandType.START_GAME, null, "player-1", null);
        doThrow(new IllegalArgumentException("At least two players are required"))
                .when(lobbyService).startGame("lobby-1");

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("At least two players");
        assertThat(response.getCommandType()).isEqualTo(CommandType.START_GAME);
        assertThat(response.getState()).isNull();
    }

    @Test
    void handleLobbyCommandReturnsErrorWhenCommandTypeIsMissing() {
        WebSocketBrokerController controller = new WebSocketBrokerController(lobbyService, gameCommandService, lobbyStore);
        ClientCommand command = new ClientCommand(null, null, "player-1", null);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Command type is required");
        assertThat(response.getCommandType()).isNull();
    }
}
