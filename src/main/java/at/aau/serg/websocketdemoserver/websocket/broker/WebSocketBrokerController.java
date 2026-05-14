package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameCommandService;
import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.game.InMemoryLobbyStore;
import at.aau.serg.websocketdemoserver.game.LobbyLeaveResult;
import at.aau.serg.websocketdemoserver.game.LobbyService;
import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.messaging.dtos.StompMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;


/**
 * Controller-Klasse, die eingehende WebSocket-Kommandos entgegennimmt und
 * diese zur Ausführung an die entsprechenden Services weiterleitet.
 */
@Controller
public class WebSocketBrokerController {
    private final LobbyService lobbyService;
    private final GameCommandService gameCommandService;
    private final InMemoryLobbyStore lobbyStore;
    private final SessionRegistry sessionRegistry;
    private final DisconnectScheduler disconnectScheduler;

    public WebSocketBrokerController(LobbyService lobbyService,
                                     GameCommandService gameCommandService,
                                     InMemoryLobbyStore lobbyStore,
                                     SessionRegistry sessionRegistry,
                                     DisconnectScheduler disconnectScheduler) {
        this.lobbyService = lobbyService;
        this.gameCommandService = gameCommandService;
        this.lobbyStore = lobbyStore;
        this.sessionRegistry = sessionRegistry;
        this.disconnectScheduler = disconnectScheduler;
    }

    @MessageMapping("/hello")
    @SendTo("/topic/hello-response")
    public String handleHello(String text) {
        // TODO handle the messages here
        return "echo from broker: "+text;
    }
    @MessageMapping("/object")
    @SendTo("/topic/rcv-object")
    public StompMessage handleObject(StompMessage msg) {

       return msg;
    }

    @MessageMapping("/lobby/{lobbyId}/command")
    @SendTo("/topic/lobby/{lobbyId}/events")
    public CommandResponse handleLobbyCommand(@DestinationVariable String lobbyId, ClientCommand command, SimpMessageHeaderAccessor headerAccessor) {
        CommandType commandType = command != null ? command.getType() : null;
        try {
            if (command == null || commandType == null) {
                throw new GameException(ErrorCode.MISSING_COMMAND_TYPE, "Command type is required");
            }
            command.setLobbyId(lobbyId);

            if (commandType == CommandType.LEAVE_LOBBY) {
                LobbyLeaveResult result = lobbyService.leaveLobby(lobbyId, command.getPlayerId());
                unregisterSession(headerAccessor);
                disconnectScheduler.cancel(lobbyId, command.getPlayerId());
                CommandType responseType = result.lobbyClosed() ? CommandType.LOBBY_CLOSED : CommandType.LEAVE_LOBBY;
                return new CommandResponse(true, "OK", null, lobbyId, responseType, result.state());
            }

            if (commandType == CommandType.REJOIN_LOBBY) {
                GameRoomState state = lobbyService.rejoinLobby(lobbyId, command.getPlayerId(), command.getClientId());
                disconnectScheduler.cancel(lobbyId, command.getPlayerId());
                registerSession(headerAccessor, lobbyId, command.getPlayerId());
                return new CommandResponse(true, "OK", null, lobbyId, CommandType.PLAYER_RECONNECTED, state);
            }

            GameRoomState state = switch (commandType) {
                case CREATE_LOBBY -> {
                    GameRoomState s = lobbyService.createLobby(lobbyId, command.getPlayerId(), command.getClientId());
                    registerSession(headerAccessor, lobbyId, command.getPlayerId());
                    yield s;
                }
                case JOIN_LOBBY -> {
                    GameRoomState s = lobbyService.joinLobby(lobbyId, command.getPlayerId(), command.getClientId());
                    registerSession(headerAccessor, lobbyId, command.getPlayerId());
                    yield s;
                }
                case UPDATE_GAME_MODE -> {
                    GameRoomState existingState = lobbyStore.get(lobbyId)
                            .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));
                    gameCommandService.processCommand(existingState, command);
                    yield existingState;
                }
                case START_GAME -> {
                    int stops = command.getStops() != null ? command.getStops() : 12;

                    var existingState = lobbyStore.get(lobbyId);

                    if (existingState != null
                            && existingState.isPresent()
                            && existingState.get().getGameMode() != null) {
                        stops = existingState.get().getGameMode().getStops();
                    }

                    yield lobbyService.startGame(lobbyId, stops);
                }
                case RESET_LOBBY -> lobbyService.resetLobby(lobbyId, command.getPlayerId());
                case ROLL_DICE, MOVE_TOKEN, MOVE_TO_CITY, END_TURN -> {
                    GameRoomState existingState = lobbyStore.get(lobbyId)
                            .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));
                    gameCommandService.processCommand(existingState, command);
                    yield existingState;
                }
                default -> throw new GameException(ErrorCode.UNSUPPORTED_COMMAND_TYPE, "Unsupported command type");
            };

            return new CommandResponse(true, "OK", null, lobbyId, commandType, state);
        } catch (GameException ex) {
            return new CommandResponse(false, ex.getMessage(), ex.getErrorCode(), lobbyId, commandType, null);
        } catch (Exception ex) {
            return new CommandResponse(false, "Internal server error", ErrorCode.INTERNAL_ERROR, lobbyId, commandType, null);
        }
    }

    private void registerSession(SimpMessageHeaderAccessor headerAccessor, String lobbyId, String playerId) {
        if (headerAccessor != null && headerAccessor.getSessionId() != null) {
            sessionRegistry.register(headerAccessor.getSessionId(), lobbyId, playerId);
        }
    }

    private void unregisterSession(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor != null && headerAccessor.getSessionId() != null) {
            sessionRegistry.remove(headerAccessor.getSessionId());
        }
    }
}
