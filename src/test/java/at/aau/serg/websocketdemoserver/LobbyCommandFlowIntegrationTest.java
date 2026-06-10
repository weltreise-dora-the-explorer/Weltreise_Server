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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LobbyCommandFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private static final String WEBSOCKET_URI = "ws://localhost:%d/websocket-example-broker";

    @Test
    void coreLobbyFlowHandlesJoinStartRollAndMove() throws Exception {
        String lobbyId = "integration-lobby-a" + UUID.randomUUID();
        // Jeder Spieler hat seine eigene WebSocket-Session – so verlangt es die
        // Session-Autorisierung (ein Client darf nicht für mehrere Spieler handeln).
        BlockingQueue<CommandResponse> p1Messages = new LinkedBlockingDeque<>();
        StompSession p1Session = initSession("/topic/lobby/" + lobbyId + "/events", p1Messages);
        BlockingQueue<CommandResponse> p2Messages = new LinkedBlockingDeque<>();
        StompSession p2Session = initSession("/topic/lobby/" + lobbyId + "/events", p2Messages);

        ClientCommand createCommand = new ClientCommand(CommandType.CREATE_LOBBY, null, "player-1", null, null);
        createCommand.setClientId("client-1");

        p1Session.send("/app/lobby/" + lobbyId + "/command", createCommand);
        CommandResponse create = p1Messages.poll(1, TimeUnit.SECONDS);
        assertThat(create).isNotNull();
        assertThat(create.isSuccess()).isTrue();
        assertThat(create.getCommandType()).isEqualTo(CommandType.CREATE_LOBBY);
        assertThat(create.getState().getPlayers()).hasSize(1);

        ClientCommand joinCommand = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-2", null, null);
        joinCommand.setClientId("client-2");

        p2Session.send("/app/lobby/" + lobbyId + "/command", joinCommand);
        CommandResponse joinTwo = p1Messages.poll(1, TimeUnit.SECONDS);
        assertThat(joinTwo).isNotNull();
        assertThat(joinTwo.isSuccess()).isTrue();
        assertThat(joinTwo.getState().getPlayers()).hasSize(2);

        p1Session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.START_GAME, null, "player-1", null, null));
        CommandResponse start = p1Messages.poll(1, TimeUnit.SECONDS);
        assertThat(start).isNotNull();
        assertThat(start.isSuccess()).isTrue();
        assertThat(start.getState().getCurrentPlayerId()).isEqualTo("player-1");

        p1Session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null, null));
        CommandResponse roll = p1Messages.poll(1, TimeUnit.SECONDS);
        assertThat(roll).isNotNull();
        assertThat(roll.isSuccess()).isTrue();
        assertThat(roll.getState().getLastDiceValue()).isBetween(1, 6);

        int rolledValue = roll.getState().getLastDiceValue();
        p1Session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.MOVE_TOKEN, null, "player-1", rolledValue, null));
        CommandResponse move = p1Messages.poll(1, TimeUnit.SECONDS);
        assertThat(move).isNotNull();
        assertThat(move.isSuccess()).isTrue();
        assertThat(move.getState().getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(move.getState().getLastDiceValue()).isNull();
    }

    @Test
    void coreLobbyFlowRejectsTurnCommandFromForeignSession() throws Exception {
        String lobbyId = "integration-lobby-spoof" + UUID.randomUUID();
        BlockingQueue<CommandResponse> p1Messages = new LinkedBlockingDeque<>();
        StompSession p1Session = initSession("/topic/lobby/" + lobbyId + "/events", p1Messages);
        BlockingQueue<CommandResponse> p2Messages = new LinkedBlockingDeque<>();
        StompSession p2Session = initSession("/topic/lobby/" + lobbyId + "/events", p2Messages);

        ClientCommand createCommand = new ClientCommand(CommandType.CREATE_LOBBY, null, "player-1", null, null);
        createCommand.setClientId("client-1");
        p1Session.send("/app/lobby/" + lobbyId + "/command", createCommand);
        assertThat(p1Messages.poll(1, TimeUnit.SECONDS)).isNotNull();

        ClientCommand joinCommand = new ClientCommand(CommandType.JOIN_LOBBY, null, "player-2", null, null);
        joinCommand.setClientId("client-2");
        p2Session.send("/app/lobby/" + lobbyId + "/command", joinCommand);
        assertThat(p1Messages.poll(1, TimeUnit.SECONDS)).isNotNull();

        // p2 ist auf denselben Broadcast-Topic abonniert und hat die create/join-Antworten
        // bereits in seiner Queue -> vor dem Spoof-Versuch entleeren.
        while (p2Messages.poll(200, TimeUnit.MILLISECONDS) != null) {
            // verwerfen
        }

        // player-2 versucht über seine eigene Session, im Namen von player-1 zu handeln.
        p2Session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.START_GAME, null, "player-1", null, null));
        CommandResponse spoof = p2Messages.poll(1, TimeUnit.SECONDS);

        assertThat(spoof).isNotNull();
        assertThat(spoof.isSuccess()).isFalse();
        assertThat(spoof.getErrorCode()).isEqualTo(ErrorCode.NOT_AUTHORIZED);
    }

    @Test
    void commandFlowReturnsNotAuthorizedForTurnCommandWithoutSession() throws Exception {
        String lobbyId = "integration-lobby-missing" + UUID.randomUUID();
        BlockingQueue<CommandResponse> messages = new LinkedBlockingDeque<>();
        StompSession session = initSession("/topic/lobby/" + lobbyId + "/events", messages);

        // Session hat nie eine Lobby erstellt/betreten -> nicht autorisiert.
        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(CommandType.ROLL_DICE, null, "player-1", null, null));
        CommandResponse response = messages.poll(1, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.NOT_AUTHORIZED);
    }

    @Test
    void commandFlowReturnsMissingCommandTypeWhenTypeIsNull() throws Exception {
        String lobbyId = "integration-lobby-b" + UUID.randomUUID();
        BlockingQueue<CommandResponse> messages = new LinkedBlockingDeque<>();
        StompSession session = initSession("/topic/lobby/" + lobbyId + "/events", messages);

        session.send("/app/lobby/" + lobbyId + "/command",
                new ClientCommand(null, null, "player-1", null, null));
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
