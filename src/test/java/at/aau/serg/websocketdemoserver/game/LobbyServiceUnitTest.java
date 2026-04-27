package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.game.LobbyLeaveResult;
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
        CityDistributor distributor = new CityDistributor();
        distributor.loadCitiesFromJson();
        service = new LobbyService(store, distributor);
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
        assertThat(store.get("lobby-1").orElseThrow().getPlayers()).hasSize(1);
    }

    @Test
    void createLobbyRejectsDuplicateLobbyId() {
        service.createLobby("lobby-1", "host-player");

        assertThatThrownBy(() -> service.createLobby("lobby-1", "another-host"))
                .isInstanceOf(GameException.class)
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
                .isInstanceOf(GameException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void joinLobbyRejectsDuplicatePlayer() {
        service.createLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", "player-1"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("already joined");
    }

    @Test
    void joinLobbyRejectsJoiningStartedGame() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        assertThatThrownBy(() -> service.joinLobby("lobby-1", "player-3"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Cannot join started game");
    }

    @Test
    void joinLobbyRejectsEmptyPlayerId() {
        service.createLobby("lobby-1", "host");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", ""))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Player id is required");
    }

    @Test
    void joinLobbyRejectsNullPlayerId() {
        service.createLobby("lobby-1", "host");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", null))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Player id is required");
    }

    @Test
    void joinLobbyAllowsFourthPlayer() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.joinLobby("lobby-1", "player-3");

        GameRoomState state = service.joinLobby("lobby-1", "player-4");

        assertThat(state.getPlayers()).hasSize(4);
    }

    @Test
    void joinLobbyRejectsWhenLobbyIsFull() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.joinLobby("lobby-1", "player-3");
        service.joinLobby("lobby-1", "player-4");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", "player-5"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Lobby is full");
    }

    // ========== START GAME TESTS ==========

    @Test
    void startGameSetsInTurnPhaseAndCurrentPlayer() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        GameRoomState state = service.startGame("lobby-1", 12);

        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(state.getLastDiceValue()).isNull();
    }

    @Test
    void startGameRejectsLobbyWithLessThanTwoPlayers() {
        service.createLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.startGame("lobby-1", 12))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("At least two players");
    }

    @Test
    void startGameRejectsNonExistentLobby() {
        assertThatThrownBy(() -> service.startGame("lobby-1", 12))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void startGameRejectsAlreadyStartedGame() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        assertThatThrownBy(() -> service.startGame("lobby-1", 12))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("already started");
    }

    // ========== LEAVE LOBBY TESTS ==========

    @Test
    void leaveLobbyRemovesCurrentPlayerAndRotatesTurn() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        GameRoomState started = service.startGame("lobby-1", 12);
        started.setLastDiceValue(5);

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "player-2");

        assertThat(result.state().getPlayers()).hasSize(1);
        assertThat(result.state().getPlayers().getFirst().getPlayerId()).isEqualTo("player-1");
        assertThat(result.lobbyClosed()).isFalse();
    }

    @Test
    void leaveLobbyRemovesLobbyWhenLastNonHostPlayerLeaves() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        service.leaveLobby("lobby-1", "player-2");
        service.leaveLobby("lobby-1", "player-1");

        assertThat(store.get("lobby-1")).isEmpty();
    }

    @Test
    void leaveLobbyRejectsNonExistentLobby() {
        assertThatThrownBy(() -> service.leaveLobby("non-existent", "player-1"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void leaveLobbyRejectsPlayerNotInLobby() {
        service.createLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.leaveLobby("lobby-1", "player-2"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not in lobby");
    }

    @Test
    void leaveLobbyDoesNotChangeTurnWhenNonCurrentPlayerLeaves() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "player-2");

        assertThat(result.state().getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(result.state().getPlayers()).hasSize(1);
    }

    @Test
    void leaveLobbyReturnsLobbyClosed_WhenHostLeaves() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "player-1");

        assertThat(result.lobbyClosed()).isTrue();
    }

    @Test
    void leaveLobbyRemovesAllPlayers_WhenHostLeaves() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.joinLobby("lobby-1", "player-3");

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "player-1");

        assertThat(result.state().getPlayers()).isEmpty();
    }

    @Test
    void leaveLobbyDeletesLobby_WhenHostLeaves() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        service.leaveLobby("lobby-1", "player-1");

        assertThat(store.get("lobby-1")).isEmpty();
    }

    @Test
    void leaveLobbyReturnsNotClosed_WhenNonHostLeaves() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "player-2");

        assertThat(result.lobbyClosed()).isFalse();
    }

    @Test
    void createLobby_SetsHostId() {
        GameRoomState state = service.createLobby("lobby-1", "host-player");

        assertThat(state.getHostId()).isEqualTo("host-player");
    }

    @Test
    void startGameAssignsCitiesToPlayers() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        GameRoomState state = service.startGame("lobby-1", 12);

        assertThat(state.getPlayers().getFirst().getOwnedCities()).isNotEmpty();
        assertThat(state.getPlayers().getFirst().getStartCity()).isNotNull();
        assertThat(state.getPlayers().getFirst().getCurrentCity()).isNotNull();
    }
}
