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
import at.aau.serg.websocketdemoserver.messaging.dtos.StompMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;

import org.springframework.stereotype.Controller;


@Controller
public class WebSocketBrokerController {
    private final LobbyService lobbyService;
    private final GameCommandService gameCommandService;
    private final InMemoryLobbyStore lobbyStore;

    public WebSocketBrokerController(LobbyService lobbyService,
                                     GameCommandService gameCommandService,
                                     InMemoryLobbyStore lobbyStore) {
        this.lobbyService = lobbyService;
        this.gameCommandService = gameCommandService;
        this.lobbyStore = lobbyStore;
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
    public CommandResponse handleLobbyCommand(@DestinationVariable String lobbyId, ClientCommand command) {
        CommandType commandType = command != null ? command.getType() : null;
        try {
            if (command == null || commandType == null) {
                throw new GameException(ErrorCode.MISSING_COMMAND_TYPE, "Command type is required");
            }
            command.setLobbyId(lobbyId);

            GameRoomState state = switch (commandType) {
                case JOIN_LOBBY -> lobbyService.joinLobby(lobbyId, command.getPlayerId());
                case LEAVE_LOBBY -> lobbyService.leaveLobby(lobbyId, command.getPlayerId());
                case START_GAME -> lobbyService.startGame(lobbyId);
                case ROLL_DICE, MOVE_TOKEN -> {
                    GameRoomState existingState = lobbyStore.get(lobbyId)
                            .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));
                    gameCommandService.processCommand(existingState, command);
                    yield existingState;
                }
            };

            return new CommandResponse(true, "OK", null, lobbyId, commandType, state);
        } catch (GameException ex) {
            return new CommandResponse(false, ex.getMessage(), ex.getErrorCode(), lobbyId, commandType, null);
        } catch (Exception ex) {
            return new CommandResponse(false, "Internal server error", ErrorCode.INTERNAL_ERROR, lobbyId, commandType, null);
        }
    }

}
