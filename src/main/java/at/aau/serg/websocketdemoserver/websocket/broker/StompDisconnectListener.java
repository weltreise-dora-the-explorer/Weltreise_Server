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
    private final DisconnectScheduler disconnectScheduler;

    public StompDisconnectListener(SessionRegistry sessionRegistry,
                                   LobbyService lobbyService,
                                   SimpMessagingTemplate messagingTemplate,
                                   DisconnectScheduler disconnectScheduler) {
        this.sessionRegistry = sessionRegistry;
        this.lobbyService = lobbyService;
        this.messagingTemplate = messagingTemplate;
        this.disconnectScheduler = disconnectScheduler;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        sessionRegistry.get(sessionId).ifPresent(info -> {
            try {
                GameRoomState state = lobbyService.markPlayerDisconnected(info.lobbyId(), info.playerId());
                broadcast(info.lobbyId(), CommandType.PLAYER_DISCONNECTED, state);
                disconnectScheduler.schedule(info.lobbyId(), info.playerId(),
                        () -> handleGracePeriodExpired(info.lobbyId(), info.playerId()));
            } catch (GameException ignored) {
                // Lobby already gone or player already removed — nothing to broadcast
            } finally {
                sessionRegistry.remove(sessionId);
            }
        });
    }

    private void handleGracePeriodExpired(String lobbyId, String playerId) {
        LobbyLeaveResult result = lobbyService.removeDisconnectedPlayer(lobbyId, playerId);
        if (result.state() == null) {
            return;
        }
        CommandType responseType = result.lobbyClosed() ? CommandType.LOBBY_CLOSED : CommandType.LEAVE_LOBBY;
        broadcast(lobbyId, responseType, result.state());
    }

    private void broadcast(String lobbyId, CommandType type, GameRoomState state) {
        CommandResponse response = new CommandResponse(true, "OK", null, lobbyId, type, state);
        messagingTemplate.convertAndSend("/topic/lobby/" + lobbyId + "/events", response);
    }
}
