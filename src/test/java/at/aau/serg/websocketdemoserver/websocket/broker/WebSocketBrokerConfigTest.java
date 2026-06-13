package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketBrokerConfigTest {

    @Test
    void webSocketSerializationDoesNotExposeClientId() throws Exception {
        GameRoomState state = new GameRoomState();
        state.getPlayers().add(new PlayerState("player-1", "secret-client-id-hash"));

        String json = JsonMapper.builder().build().writeValueAsString(state);

        assertThat(json).contains("\"playerId\":\"player-1\"");
        assertThat(json).doesNotContain("clientId");
        assertThat(json).doesNotContain("secret-client-id-hash");
    }
}
