package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LobbyServiceUnitTest {

    private InMemoryLobbyStore store;
    private LobbyService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryLobbyStore();
        service = new LobbyService(store, new CityDistributor());
    }

    // ========== CREATE LOBBY TESTS ==========

    @Test
    void createLobbyCreatesNewLobbyWithHost() {
        GameRoomState state = service.createLobby("lobby-1", "host-player");

        assertThat(state.getLobbyId()).isEqualTo("lobby-1");
        assertThat(state.getPlayers()).hasSize(1);
        assertThat(state.getPlayers().getFirst().getPlayerId()).isEqualTo("host-player");
        assertThat(state.getPhase()).isEqualTo(GamePhase.LOBBY);
    }

    @Test
    void createLobbyStoresLobbyInStore() {
        service.createLobby("lobby-1", "host-player");

        assertThat(store.get("lobby-1")).isPresent();
        assertThat(store.get("lobby-1").get().getPlayers()).hasSize(1);
    }

    @Test
    void createLobbyRejectsDuplicateLobbyId() {
        service.createLobby("lobby-1", "host-player");

        assertThatThrownBy(() -> service.createLobby("lobby-1", "another-host"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ========== JOIN LOBBY TESTS ==========

    @Test
    void joinLobbyAddsPlayerToExistingLobby() {
        service.createLobby("lobby-1", "host-player");

        GameRoomState state = service.joinLobby("lobby-1", "player-2");

        assertThat(state.getPlayers()).hasSize(2);
        assertThat(state.getPlayers().get(1).getPlayerId()).isEqualTo("player-2");
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void joinLobbyRejectsNonExistentLobby() {
        assertThatThrownBy(() -> service.joinLobby("non-existent", "player-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void joinLobbyRejectsDuplicatePlayer() {
        service.createLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", "player-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already joined");
    }

    @Test
    void joinLobbyRejectsJoiningStartedGame() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", "player-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot join started game");
    }

    @Test
    void joinLobbyRejectsEmptyPlayerId() {
        service.createLobby("lobby-1", "host");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Player id is required");
    }

    @Test
    void joinLobbyRejectsNullPlayerId() {
        service.createLobby("lobby-1", "host");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Player id is required");
    }

    // ========== START GAME TESTS ==========

    @Test
    void startGameSetsInTurnPhaseAndCurrentPlayer() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        GameRoomState state = service.startGame("lobby-1");

        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(state.getLastDiceValue()).isNull();
    }

    @Test
    void startGameRejectsLobbyWithLessThanTwoPlayers() {
        service.createLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.startGame("lobby-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least two players");
    }

    @Test
    void startGameRejectsNonExistentLobby() {
        assertThatThrownBy(() -> service.startGame("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void startGameRejectsAlreadyStartedGame() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1");

        assertThatThrownBy(() -> service.startGame("lobby-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already started");
    }

    // ========== LEAVE LOBBY TESTS ==========

    @Test
    void leaveLobbyRemovesCurrentPlayerAndRotatesTurn() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        GameRoomState started = service.startGame("lobby-1");
        started.setLastDiceValue(5);

        GameRoomState state = service.leaveLobby("lobby-1", "player-1");

        assertThat(state.getPlayers()).hasSize(1);
        assertThat(state.getPlayers().getFirst().getPlayerId()).isEqualTo("player-2");
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(state.getLastDiceValue()).isNull();
    }

    @Test
    void leaveLobbyRemovesLobbyWhenLastPlayerLeaves() {
        service.createLobby("lobby-1", "player-1");

        service.leaveLobby("lobby-1", "player-1");

        assertThat(store.get("lobby-1")).isEmpty();
    }

    @Test
    void leaveLobbyRejectsNonExistentLobby() {
        assertThatThrownBy(() -> service.leaveLobby("non-existent", "player-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void leaveLobbyRejectsPlayerNotInLobby() {
        service.createLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.leaveLobby("lobby-1", "player-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in lobby");
    }
}
