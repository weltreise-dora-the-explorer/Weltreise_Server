package at.aau.serg.websocketdemoserver;

import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandResponse;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import at.aau.serg.websocketdemoserver.websocket.StompFrameHandlerClientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyCommandFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private static final String WEBSOCKET_URI = "ws://localhost:%d/websocket-example-broker";

    @Test
    void coreLobbyFlowHandlesJoinStartRollAndMove() throws Exception {
        String lobbyId = "integration-lobby-a";
        BlockingQueue<CommandResponse> messages = new LinkedBlockingDeque<>();
        StompSession session = initSession("/topic/lobby/" + lobbyId + "/events", messages);

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.JOIN_LOBBY, null, "player-1", null));
        CommandResponse joinOne = messages.poll(1, TimeUnit.SECONDS);
        assertThat(joinOne).isNotNull();
        assertThat(joinOne.isSuccess()).isTrue();
        assertThat(joinOne.getCommandType()).isEqualTo(CommandType.JOIN_LOBBY);
        assertThat(joinOne.getState().getPlayers()).hasSize(1);

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.JOIN_LOBBY, null, "player-2", null));
        CommandResponse joinTwo = messages.poll(1, TimeUnit.SECONDS);
        assertThat(joinTwo).isNotNull();
        assertThat(joinTwo.isSuccess()).isTrue();
        assertThat(joinTwo.getState().getPlayers()).hasSize(2);

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.START_GAME, null, "player-1", null));
        CommandResponse start = messages.poll(1, TimeUnit.SECONDS);
        assertThat(start).isNotNull();
        assertThat(start.isSuccess()).isTrue();
        assertThat(start.getState().getCurrentPlayerId()).isEqualTo("player-1");

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null));
        CommandResponse roll = messages.poll(1, TimeUnit.SECONDS);
        assertThat(roll).isNotNull();
        assertThat(roll.isSuccess()).isTrue();
        assertThat(roll.getState().getLastDiceValue()).isBetween(1, 6);

        int rolledValue = roll.getState().getLastDiceValue();
        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.MOVE_TOKEN, null, "player-1", rolledValue));
        CommandResponse move = messages.poll(1, TimeUnit.SECONDS);
        assertThat(move).isNotNull();
        assertThat(move.isSuccess()).isTrue();
        assertThat(move.getState().getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(move.getState().getLastDiceValue()).isNull();
    }

    @Test
    void commandFlowReturnsLobbyNotFoundForTurnCommandWithoutLobby() throws Exception {
        String lobbyId = "integration-lobby-missing";
        BlockingQueue<CommandResponse> messages = new LinkedBlockingDeque<>();
        StompSession session = initSession("/topic/lobby/" + lobbyId + "/events", messages);

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null));
        CommandResponse response = messages.poll(1, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.LOBBY_NOT_FOUND);
    }

    @Test
    void commandFlowReturnsMissingCommandTypeWhenTypeIsNull() throws Exception {
        String lobbyId = "integration-lobby-b";
        BlockingQueue<CommandResponse> messages = new LinkedBlockingDeque<>();
        StompSession session = initSession("/topic/lobby/" + lobbyId + "/events", messages);

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(null, null, "player-1", null));
        CommandResponse response = messages.poll(1, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.MISSING_COMMAND_TYPE);
    }

    private StompSession initSession(String destination, BlockingQueue<CommandResponse> queue) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        StompSession session = stompClient.connectAsync(String.format(WEBSOCKET_URI, port),
                        new StompSessionHandlerAdapter() {
                        })
                .get(1, TimeUnit.SECONDS);

        session.subscribe(destination, new StompFrameHandlerClientImpl<>(queue, CommandResponse.class));
        return session;
    }
}
