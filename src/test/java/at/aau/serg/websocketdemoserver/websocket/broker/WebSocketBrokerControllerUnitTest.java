package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameCommandService;
import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.game.InMemoryLobbyStore;
import at.aau.serg.websocketdemoserver.game.LobbyService;
import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketBrokerControllerUnitTest {

    @Mock
    private LobbyService lobbyService;
    @Mock
    private GameCommandService gameCommandService;
    @Mock
    private InMemoryLobbyStore lobbyStore;
    @Mock
    private SessionRegistry sessionRegistry;

    private WebSocketBrokerController createController() {
        return new WebSocketBrokerController(lobbyService, gameCommandService, lobbyStore, sessionRegistry);
    }

    private SimpMessageHeaderAccessor headerWithSession(String sessionId) {
        SimpMessageHeaderAccessor ha = SimpMessageHeaderAccessor.create();
        ha.setSessionId(sessionId);
        return ha;
    }

    @Test
    void handleLobbyCommandRoutesCreateLobbyAndReturnsSuccessResponse() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.CREATE_LOBBY, null, "host-player", null, null);
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        when(lobbyService.createLobby("lobby-1", "host-player")).thenReturn(state);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getLobbyId()).isEqualTo("lobby-1");
        assertThat(response.getCommandType()).isEqualTo(CommandType.CREATE_LOBBY);
        assertThat(response.getState()).isEqualTo(state);
        verify(lobbyService).createLobby("lobby-1", "host-player");
    }

    @Test
    void handleLobbyCommandRegistersSessionOnCreateLobby() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.CREATE_LOBBY, null, "host-player", null, null);
        when(lobbyService.createLobby("lobby-1", "host-player")).thenReturn(new GameRoomState());

        controller.handleLobbyCommand("lobby-1", command, headerWithSession("session-abc"));

        verify(sessionRegistry).register("session-abc", "lobby-1", "host-player");
    }

    @Test
    void handleLobbyCommandRegistersSessionOnJoinLobby() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-1", null, null);
        when(lobbyService.joinLobby("lobby-1", "player-1")).thenReturn(new GameRoomState());

        controller.handleLobbyCommand("lobby-1", command, headerWithSession("session-xyz"));

        verify(sessionRegistry).register("session-xyz", "lobby-1", "player-1");
    }

    @Test
    void handleLobbyCommandUnregistersSessionOnLeaveLobby() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.LEAVE_LOBBY, null, "player-1", null, null);
        when(lobbyService.leaveLobby("lobby-1", "player-1")).thenReturn(new GameRoomState());

        controller.handleLobbyCommand("lobby-1", command, headerWithSession("session-xyz"));

        verify(sessionRegistry).remove("session-xyz");
    }

    @Test
    void handleLobbyCommandDoesNotRegisterSessionWhenHeaderAccessorIsNull() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.CREATE_LOBBY, null, "host-player", null, null);
        when(lobbyService.createLobby("lobby-1", "host-player")).thenReturn(new GameRoomState());

        controller.handleLobbyCommand("lobby-1", command, null);

        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void handleLobbyCommandReturnsErrorWhenLobbyAlreadyExists() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.CREATE_LOBBY, null, "host-player", null, null);

        doThrow(new GameException(ErrorCode.GAME_ALREADY_STARTED, "Lobby already exists"))
                .when(lobbyService).createLobby("lobby-1", "host-player");

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("already exists");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.GAME_ALREADY_STARTED);
        assertThat(response.getCommandType()).isEqualTo(CommandType.CREATE_LOBBY);
    }

    @Test
    void handleLobbyCommandRoutesJoinAndReturnsSuccessResponse() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-1", null, null);
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        when(lobbyService.joinLobby("lobby-1", "player-1")).thenReturn(state);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getLobbyId()).isEqualTo("lobby-1");
        assertThat(response.getCommandType()).isEqualTo(CommandType.JOIN_LOBBY);
        assertThat(response.getState()).isEqualTo(state);
        verify(lobbyService).joinLobby("lobby-1", "player-1");
    }

    @Test
    void handleLobbyCommandReturnsErrorWhenLobbyDoesNotExist() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-1", null, null);

        doThrow(new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby does not exist"))
                .when(lobbyService).joinLobby("lobby-1", "player-1");

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("does not exist");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.LOBBY_NOT_FOUND);
        assertThat(response.getCommandType()).isEqualTo(CommandType.JOIN_LOBBY);
    }

    @Test
    void handleLobbyCommandRoutesRollDiceThroughGameService() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null, null);
        GameRoomState state = new GameRoomState();
        when(lobbyStore.get("lobby-1")).thenReturn(Optional.of(state));

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCommandType()).isEqualTo(CommandType.ROLL_DICE);
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getState()).isEqualTo(state);
        verify(gameCommandService).processCommand(state, command);
    }

    @Test
    void handleLobbyCommandReturnsErrorResponseOnValidationFailure() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.START_GAME, null, "player-1", null, null);
        doThrow(new GameException(ErrorCode.MIN_PLAYERS_NOT_REACHED, "At least two players are required"))
                .when(lobbyService).startGame("lobby-1", 12);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("At least two players");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.MIN_PLAYERS_NOT_REACHED);
        assertThat(response.getCommandType()).isEqualTo(CommandType.START_GAME);
        assertThat(response.getState()).isNull();
    }

    @Test
    void handleLobbyCommandReturnsErrorWhenCommandTypeIsMissing() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(null, null, "player-1", null, null);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Command type is required");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.MISSING_COMMAND_TYPE);
        assertThat(response.getCommandType()).isNull();
    }

    @Test
    void handleLobbyCommandReturnsInternalErrorOnUnexpectedException() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.START_GAME, null, "player-1", null, null);
        doThrow(new RuntimeException("unexpected"))
                .when(lobbyService).startGame("lobby-1", 12);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Internal server error");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void handleLobbyCommandReturnsErrorWhenCommandIsNull() {
        WebSocketBrokerController controller = createController();

        CommandResponse response = controller.handleLobbyCommand("lobby-1", null, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Command type is required");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.MISSING_COMMAND_TYPE);
    }

    @Test
    void handleLobbyCommandReturnsLobbyFullErrorWhenLobbyIsFull() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-5", null, null);

        doThrow(new GameException(ErrorCode.LOBBY_FULL, "Lobby is full"))
                .when(lobbyService).joinLobby("lobby-1", "player-5");

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).contains("Lobby is full");
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.LOBBY_FULL);
        assertThat(response.getCommandType()).isEqualTo(CommandType.JOIN_LOBBY);
    }

    @Test
    void handleLobbyCommandReturnsErrorWhenLobbyNotFoundForRollDice() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null, null);
        when(lobbyStore.get("lobby-1")).thenReturn(java.util.Optional.empty());

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.LOBBY_NOT_FOUND);
    }

    @Test
    void handleLobbyCommandRoutesLeaveLobbyAndReturnsSuccessResponse() {
        WebSocketBrokerController controller = createController();
        ClientCommand command = new ClientCommand(CommandType.LEAVE_LOBBY, null, "player-1", null, null);
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        when(lobbyService.leaveLobby("lobby-1", "player-1")).thenReturn(state);

        CommandResponse response = controller.handleLobbyCommand("lobby-1", command, headerWithSession("s1"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCommandType()).isEqualTo(CommandType.LEAVE_LOBBY);
        assertThat(response.getState()).isEqualTo(state);
        verify(lobbyService).leaveLobby("lobby-1", "player-1");
    }
}
