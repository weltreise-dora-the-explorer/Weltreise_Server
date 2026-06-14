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
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.EnumSet;
import java.util.Set;


/**
 * Controller-Klasse, die eingehende WebSocket-Kommandos entgegennimmt und
 * diese zur Ausführung an die entsprechenden Services weiterleitet.
 */
@Controller
public class WebSocketBrokerController {

    /**
     * Commands, die im Namen eines bereits angemeldeten Spielers ausgeführt
     * werden. Sie dürfen nur von der WebSocket-Session abgesetzt werden, die
     * tatsächlich zu dieser {@code playerId} gehört (siehe {@link #requireAuthorizedSession}).
     * Ausgenommen sind {@code CREATE_LOBBY}, {@code JOIN_LOBBY} und
     * {@code REJOIN_LOBBY}, da diese die Session-Identität erst etablieren.
     */
    private static final Set<CommandType> PROTECTED_COMMANDS = EnumSet.of(
            CommandType.UPDATE_GAME_MODE,
            CommandType.START_GAME,
            CommandType.RESET_LOBBY,
            CommandType.ROLL_DICE,
            CommandType.MOVE_TOKEN,
            CommandType.MOVE_TO_CITY,
            CommandType.END_TURN,
            CommandType.START_MINIGAME,
            CommandType.SUBMIT_GUESS,
            CommandType.ANNOUNCE_MINIGAME_RESULT,
            CommandType.FINISH_MINIGAME,
            CommandType.REACTION_READY,
            CommandType.REACTION_PRESS,
            CommandType.USE_FREE_PASS,
            CommandType.USE_SHAKE_CHEAT,
            CommandType.REPORT_CHEAT,
            CommandType.LEAVE_LOBBY
    );

    private final LobbyService lobbyService;
    private final GameCommandService gameCommandService;
    private final InMemoryLobbyStore lobbyStore;
    private final SessionRegistry sessionRegistry;
    private final DisconnectScheduler disconnectScheduler;
    private final WebSocketCommandRateLimiter rateLimiter;

    public WebSocketBrokerController(LobbyService lobbyService,
                                     GameCommandService gameCommandService,
                                     InMemoryLobbyStore lobbyStore,
                                     SessionRegistry sessionRegistry,
                                     DisconnectScheduler disconnectScheduler,
                                     WebSocketCommandRateLimiter rateLimiter) {
        this.lobbyService = lobbyService;
        this.gameCommandService = gameCommandService;
        this.lobbyStore = lobbyStore;
        this.sessionRegistry = sessionRegistry;
        this.disconnectScheduler = disconnectScheduler;
        this.rateLimiter = rateLimiter;
    }

    @MessageMapping("/lobby/{lobbyId}/command")
    @SendTo("/topic/lobby/{lobbyId}/events")
    public CommandResponse handleLobbyCommand(@DestinationVariable String lobbyId, ClientCommand command, SimpMessageHeaderAccessor headerAccessor) {
        CommandType commandType = command != null ? command.getType() : null;
        try {
            rateLimiter.check(sessionId(headerAccessor), commandType);

            if (command == null || commandType == null) {
                throw new GameException(ErrorCode.MISSING_COMMAND_TYPE, "Command type is required");
            }
            command.setLobbyId(lobbyId);

            if (commandType == CommandType.CREATE_LOBBY
                    || commandType == CommandType.JOIN_LOBBY
                    || commandType == CommandType.REJOIN_LOBBY) {
                requireClientId(command);
            }

            if (PROTECTED_COMMANDS.contains(commandType)) {
                requireAuthorizedSession(headerAccessor, lobbyId, command);
            }

            if (commandType == CommandType.LEAVE_LOBBY) {
                LobbyLeaveResult result = lobbyService.leaveLobby(lobbyId, command.getPlayerId());
                unregisterSession(headerAccessor);
                disconnectScheduler.cancel(lobbyId, command.getPlayerId());
                CommandType responseType = result.lobbyClosed() ? CommandType.LOBBY_CLOSED : CommandType.LEAVE_LOBBY;

                if (result.state() != null) {
                    result.state().setServerNowMs(System.currentTimeMillis());
                }

                return new CommandResponse(true, "OK", null, lobbyId, responseType, result.state());
            }

            if (commandType == CommandType.REJOIN_LOBBY) {
                GameRoomState state = lobbyService.rejoinLobby(lobbyId, command.getPlayerId(), command.getClientId());
                disconnectScheduler.cancel(lobbyId, command.getPlayerId());
                registerSession(headerAccessor, lobbyId, command.getPlayerId());

                state.setServerNowMs(System.currentTimeMillis());

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
                    lobbyStore.save();
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
                case RESET_LOBBY -> lobbyService.resetLobby(command.getLobbyId(), command.getPlayerId());

                case ROLL_DICE, MOVE_TOKEN, MOVE_TO_CITY, END_TURN, START_MINIGAME, ANNOUNCE_MINIGAME_RESULT, FINISH_MINIGAME, REACTION_READY, REACTION_PRESS, USE_FREE_PASS, USE_SHAKE_CHEAT, REPORT_CHEAT -> {
                    GameRoomState existingState = lobbyStore.get(lobbyId)
                            .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));
                    gameCommandService.processCommand(existingState, command);
                    lobbyStore.save();
                    yield existingState;
                }
                case SUBMIT_GUESS -> {
                    GameRoomState existingState = lobbyStore.get(lobbyId)
                            .orElseThrow(() -> new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found"));
                    Integer guess = command.getGuess();
                    if (guess == null) {
                        throw new GameException(ErrorCode.INVALID_COMMAND, "Guess value is required");
                    }
                    gameCommandService.handleSubmitGuess(existingState, command.getPlayerId(), guess);
                    lobbyStore.save();
                    yield existingState;
                }
                default -> throw new GameException(ErrorCode.UNSUPPORTED_COMMAND_TYPE, "Unsupported command type");
            };

            state.setServerNowMs(System.currentTimeMillis());
            return new CommandResponse(true, "OK", null, lobbyId, commandType, state);
        } catch (GameException ex) {
            return new CommandResponse(false, ex.getMessage(), ex.getErrorCode(), lobbyId, commandType, null);
        } catch (Exception ex) {
            return new CommandResponse(false, "Internal server error", ErrorCode.INTERNAL_ERROR, lobbyId, commandType, null);
        }
    }

    /**
     * Stellt sicher, dass der Aufrufer wirklich der Spieler ist, für den er
     * handeln will. Verglichen wird die {@code playerId} aus dem Command mit
     * der {@link SessionRegistry.SessionInfo}, die beim Beitreten an die
     * WebSocket-Session gebunden wurde. So kann Spieler 1 keine Commands im
     * Namen von Spieler 2 absetzen.
     */
    private void requireAuthorizedSession(SimpMessageHeaderAccessor headerAccessor, String lobbyId, ClientCommand command) {
        String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
        SessionRegistry.SessionInfo info = sessionId != null
                ? sessionRegistry.get(sessionId).orElse(null)
                : null;

        if (info == null
                || !lobbyId.equals(info.lobbyId())
                || command.getPlayerId() == null
                || !command.getPlayerId().equals(info.playerId())) {
            throw new GameException(ErrorCode.NOT_AUTHORIZED,
                    "Session is not authorized to act as player '" + command.getPlayerId() + "'");
        }
    }

    private void requireClientId(ClientCommand command) {
        if (command.getClientId() == null || command.getClientId().isBlank()) {
            throw new GameException(ErrorCode.MISSING_CLIENT_ID, "Client id is required");
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

    private String sessionId(SimpMessageHeaderAccessor headerAccessor) {
        return headerAccessor != null ? headerAccessor.getSessionId() : null;
    }
}
