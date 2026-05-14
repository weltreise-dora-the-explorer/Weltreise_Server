package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.game.LobbyLeaveResult;
import at.aau.serg.websocketdemoserver.game.LobbyService;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class StompDisconnectListener {

    private final SessionRegistry sessionRegistry;
    private final LobbyService lobbyService;
    private final SimpMessagingTemplate messagingTemplate;

    public StompDisconnectListener(SessionRegistry sessionRegistry, LobbyService lobbyService, SimpMessagingTemplate messagingTemplate) {
        this.sessionRegistry = sessionRegistry;
        this.lobbyService = lobbyService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        sessionRegistry.get(sessionId).ifPresent(info -> {
            try {
                LobbyLeaveResult result = lobbyService.leaveLobby(info.lobbyId(), info.playerId());
                CommandType responseType = result.lobbyClosed() ? CommandType.LOBBY_CLOSED : CommandType.LEAVE_LOBBY;
                CommandResponse response = new CommandResponse(
                        true, "OK", null, info.lobbyId(), responseType, result.state()
                );
                messagingTemplate.convertAndSend("/topic/lobby/" + info.lobbyId() + "/events", response);
            } catch (GameException ignored) {
                // Lobby already gone or player already removed — nothing to broadcast
            } finally {
                sessionRegistry.remove(sessionId);
            }
        });
    }
}
