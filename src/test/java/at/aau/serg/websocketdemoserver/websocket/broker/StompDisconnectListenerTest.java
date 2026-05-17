package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.game.LobbyLeaveResult;
import at.aau.serg.websocketdemoserver.game.LobbyService;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompDisconnectListenerTest {

    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private LobbyService lobbyService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private DisconnectScheduler disconnectScheduler;
    @Mock
    private SessionDisconnectEvent event;

    private StompDisconnectListener createListener() {
        return new StompDisconnectListener(sessionRegistry, lobbyService, messagingTemplate, disconnectScheduler);
    }

    @Test
    void handleDisconnectBroadcastsPlayerDisconnectedAndSchedulesTimer() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        when(lobbyService.markPlayerDisconnected("lobby-1", "player-1")).thenReturn(state);

        createListener().handleDisconnect(event);

        ArgumentCaptor<CommandResponse> captor = ArgumentCaptor.forClass(CommandResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/lobby/lobby-1/events"), captor.capture());
        assertThat(captor.getValue().getCommandType()).isEqualTo(CommandType.PLAYER_DISCONNECTED);
        assertThat(captor.getValue().getState()).isEqualTo(state);
        verify(disconnectScheduler).schedule(eq("lobby-1"), eq("player-1"), any(Runnable.class));
        verify(sessionRegistry).remove("session-1");
    }

    @Test
    void handleDisconnectDoesNothingForUnknownSession() {
        when(event.getSessionId()).thenReturn("unknown-session");
        when(sessionRegistry.get("unknown-session")).thenReturn(Optional.empty());

        createListener().handleDisconnect(event);

        verifyNoInteractions(lobbyService);
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(disconnectScheduler);
        verify(sessionRegistry, never()).remove(any());
    }

    @Test
    void handleDisconnectStillRemovesSessionWhenMarkFails() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        when(lobbyService.markPlayerDisconnected("lobby-1", "player-1"))
                .thenThrow(new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        createListener().handleDisconnect(event);

        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(disconnectScheduler);
        verify(sessionRegistry).remove("session-1");
    }

    @Test
    void gracePeriodTimeoutBroadcastsLeaveLobby() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        when(lobbyService.markPlayerDisconnected("lobby-1", "player-1")).thenReturn(new GameRoomState());
        GameRoomState removedState = new GameRoomState();
        removedState.setLobbyId("lobby-1");
        when(lobbyService.removeDisconnectedPlayer("lobby-1", "player-1"))
                .thenReturn(new LobbyLeaveResult(removedState, false));

        createListener().handleDisconnect(event);
        Runnable timeoutCallback = captureScheduledCallback();
        timeoutCallback.run();

        ArgumentCaptor<CommandResponse> captor = ArgumentCaptor.forClass(CommandResponse.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/lobby/lobby-1/events"), captor.capture());
        CommandResponse second = captor.getAllValues().get(1);
        assertThat(second.getCommandType()).isEqualTo(CommandType.LEAVE_LOBBY);
        assertThat(second.getState()).isEqualTo(removedState);
    }

    @Test
    void gracePeriodTimeoutBroadcastsLobbyClosedWhenHostRemoved() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "host-player")));
        when(lobbyService.markPlayerDisconnected("lobby-1", "host-player")).thenReturn(new GameRoomState());
        GameRoomState removedState = new GameRoomState();
        removedState.setLobbyId("lobby-1");
        when(lobbyService.removeDisconnectedPlayer("lobby-1", "host-player"))
                .thenReturn(new LobbyLeaveResult(removedState, true));

        createListener().handleDisconnect(event);
        Runnable timeoutCallback = captureScheduledCallback();
        timeoutCallback.run();

        ArgumentCaptor<CommandResponse> captor = ArgumentCaptor.forClass(CommandResponse.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/lobby/lobby-1/events"), captor.capture());
        CommandResponse second = captor.getAllValues().get(1);
        assertThat(second.getCommandType()).isEqualTo(CommandType.LOBBY_CLOSED);
    }

    @Test
    void gracePeriodTimeoutDoesNotBroadcastWhenLobbyAlreadyGone() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        when(lobbyService.markPlayerDisconnected("lobby-1", "player-1")).thenReturn(new GameRoomState());
        when(lobbyService.removeDisconnectedPlayer("lobby-1", "player-1"))
                .thenReturn(new LobbyLeaveResult(null, false));

        createListener().handleDisconnect(event);
        Runnable timeoutCallback = captureScheduledCallback();
        timeoutCallback.run();

        // Only the initial PLAYER_DISCONNECTED broadcast — no second broadcast on timeout
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/lobby/lobby-1/events"), any(CommandResponse.class));
    }

    private Runnable captureScheduledCallback() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(disconnectScheduler).schedule(eq("lobby-1"), any(String.class), captor.capture());
        return captor.getValue();
    }
}
