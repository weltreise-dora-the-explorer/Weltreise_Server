package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameException;
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
    private SessionDisconnectEvent event;

    @Test
    void handleDisconnectRemovesPlayerAndBroadcastsLeaveEvent() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        when(lobbyService.leaveLobby("lobby-1", "player-1")).thenReturn(state);

        StompDisconnectListener listener = new StompDisconnectListener(sessionRegistry, lobbyService, messagingTemplate);
        listener.handleDisconnect(event);

        ArgumentCaptor<CommandResponse> captor = ArgumentCaptor.forClass(CommandResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/lobby/lobby-1/events"), captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getCommandType()).isEqualTo(CommandType.LEAVE_LOBBY);
        assertThat(captor.getValue().getState()).isEqualTo(state);
        verify(sessionRegistry).remove("session-1");
    }

    @Test
    void handleDisconnectDoesNothingForUnknownSession() {
        when(event.getSessionId()).thenReturn("unknown-session");
        when(sessionRegistry.get("unknown-session")).thenReturn(Optional.empty());

        StompDisconnectListener listener = new StompDisconnectListener(sessionRegistry, lobbyService, messagingTemplate);
        listener.handleDisconnect(event);

        verifyNoInteractions(lobbyService);
        verifyNoInteractions(messagingTemplate);
        verify(sessionRegistry, never()).remove(any());
    }

    @Test
    void handleDisconnectRemovesSessionEvenWhenLobbyAlreadyGone() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        when(lobbyService.leaveLobby("lobby-1", "player-1"))
                .thenThrow(new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));

        StompDisconnectListener listener = new StompDisconnectListener(sessionRegistry, lobbyService, messagingTemplate);
        listener.handleDisconnect(event);

        verifyNoInteractions(messagingTemplate);
        verify(sessionRegistry).remove("session-1");
    }

    @Test
    void handleDisconnectRemovesSessionEvenWhenPlayerNotInLobby() {
        when(event.getSessionId()).thenReturn("session-1");
        when(sessionRegistry.get("session-1")).thenReturn(Optional.of(new SessionRegistry.SessionInfo("lobby-1", "player-1")));
        when(lobbyService.leaveLobby("lobby-1", "player-1"))
                .thenThrow(new GameException(ErrorCode.PLAYER_NOT_IN_LOBBY, "Player not in lobby"));

        StompDisconnectListener listener = new StompDisconnectListener(sessionRegistry, lobbyService, messagingTemplate);
        listener.handleDisconnect(event);

        verify(sessionRegistry).remove("session-1");
    }
}
