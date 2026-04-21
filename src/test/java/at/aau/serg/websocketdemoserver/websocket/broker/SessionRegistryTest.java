package at.aau.serg.websocketdemoserver.websocket.broker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    @Test
    void registerAndGetReturnsSessionInfo() {
        registry.register("session-1", "lobby-1", "player-1");

        SessionRegistry.SessionInfo info = registry.get("session-1").orElseThrow();

        assertThat(info.lobbyId()).isEqualTo("lobby-1");
        assertThat(info.playerId()).isEqualTo("player-1");
    }

    @Test
    void getReturnsEmptyForUnknownSession() {
        assertThat(registry.get("unknown")).isEmpty();
    }

    @Test
    void removeDeletesSession() {
        registry.register("session-1", "lobby-1", "player-1");

        registry.remove("session-1");

        assertThat(registry.get("session-1")).isEmpty();
    }

    @Test
    void removeOnNonExistentSessionDoesNotThrow() {
        registry.remove("does-not-exist");

        assertThat(registry.get("does-not-exist")).isEmpty();
    }

    @Test
    void registerOverwritesExistingEntry() {
        registry.register("session-1", "lobby-1", "player-1");
        registry.register("session-1", "lobby-2", "player-2");

        SessionRegistry.SessionInfo info = registry.get("session-1").orElseThrow();

        assertThat(info.lobbyId()).isEqualTo("lobby-2");
        assertThat(info.playerId()).isEqualTo("player-2");
    }

    @Test
    void multipleSessionsAreStoredIndependently() {
        registry.register("session-1", "lobby-1", "player-1");
        registry.register("session-2", "lobby-1", "player-2");

        assertThat(registry.get("session-1").orElseThrow().playerId()).isEqualTo("player-1");
        assertThat(registry.get("session-2").orElseThrow().playerId()).isEqualTo("player-2");
    }

    @Test
    void removingOneSessionDoesNotAffectOthers() {
        registry.register("session-1", "lobby-1", "player-1");
        registry.register("session-2", "lobby-1", "player-2");

        registry.remove("session-1");

        assertThat(registry.get("session-1")).isEmpty();
        assertThat(registry.get("session-2")).isPresent();
    }
}
