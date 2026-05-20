package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

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
        service.joinLobby("lobby-1", "player-3");
        service.startGame("lobby-1", 12);

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "player-3");

        assertThat(result.state().getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(result.state().getPlayers()).hasSize(2);
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

    // ========== RESET LOBBY TESTS ==========

    @Test
    void resetLobbySetsPhasBackToLobby() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        GameRoomState state = service.resetLobby("lobby-1", "player-1");

        assertThat(state.getPhase()).isEqualTo(GamePhase.LOBBY);
    }

    @Test
    void resetLobbyClearsCurrentPlayerAndDice() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        GameRoomState state = service.resetLobby("lobby-1", "player-1");

        assertThat(state.getCurrentPlayerId()).isNull();
        assertThat(state.getLastDiceValue()).isNull();
    }

    @Test
    void resetLobbySetsGameOverToFalse() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);
        GameRoomState state = store.get("lobby-1").orElseThrow();
        state.setGameOver(true);

        service.resetLobby("lobby-1", "player-1");

        assertThat(state.isGameOver()).isFalse();
    }

    @Test
    void resetLobbyKeepsAllPlayers() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        GameRoomState state = service.resetLobby("lobby-1", "player-1");

        assertThat(state.getPlayers()).hasSize(2);
    }

    @Test
    void resetLobbyClosesCityDataForAllPlayers() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);

        GameRoomState state = service.resetLobby("lobby-1", "player-1");

        state.getPlayers().forEach(p -> {
            assertThat(p.getStartCity()).isNull();
            assertThat(p.getCurrentCity()).isNull();
            assertThat(p.getOwnedCities()).isEmpty();
            assertThat(p.getVisitedCities()).isEmpty();
        });
    }

    @Test
    void resetLobbyIncrementsVersion() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);
        long versionBefore = store.get("lobby-1").orElseThrow().getVersion();

        service.resetLobby("lobby-1", "player-1");

        assertThat(store.get("lobby-1").orElseThrow().getVersion()).isGreaterThan(versionBefore);
    }

    @Test
    void resetLobbyRejectsNonHost() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        assertThatThrownBy(() -> service.resetLobby("lobby-1", "player-2"))
                .isInstanceOf(GameException.class);
    }

    @Test
    void resetLobbyRejectsNonExistentLobby() {
        assertThatThrownBy(() -> service.resetLobby("non-existent", "player-1"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void resetLobbyKeepsGameMode() {
        service.createLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        service.startGame("lobby-1", 12);
        var modeBefore = store.get("lobby-1").orElseThrow().getGameMode();

        GameRoomState state = service.resetLobby("lobby-1", "player-1");

        assertThat(state.getGameMode()).isEqualTo(modeBefore);
    }

    // ========== CLIENT ID PROPAGATION ==========

    @Test
    void createLobbyStoresClientIdOnHost() {
        GameRoomState state = service.createLobby("lobby-1", "host", "client-aaa");

        assertThat(state.getPlayers().getFirst().getClientId()).isEqualTo("client-aaa");
        assertThat(state.getPlayers().getFirst().isConnected()).isTrue();
    }

    @Test
    void createLobbyWithoutClientIdLeavesItNull() {
        GameRoomState state = service.createLobby("lobby-1", "host");

        assertThat(state.getPlayers().getFirst().getClientId()).isNull();
        assertThat(state.getPlayers().getFirst().isConnected()).isTrue();
    }

    @Test
    void joinLobbyStoresClientIdOnGuest() {
        service.createLobby("lobby-1", "host", "client-aaa");

        GameRoomState state = service.joinLobby("lobby-1", "guest", "client-bbb");

        assertThat(state.getPlayers().get(1).getClientId()).isEqualTo("client-bbb");
        assertThat(state.getPlayers().get(1).isConnected()).isTrue();
    }

    // ========== MARK PLAYER DISCONNECTED ==========

    @Test
    void markPlayerDisconnectedSetsConnectedFalseAndIncrementsVersion() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");
        long versionBefore = store.get("lobby-1").orElseThrow().getVersion();

        GameRoomState state = service.markPlayerDisconnected("lobby-1", "guest");

        assertThat(state.getPlayers().get(1).isConnected()).isFalse();
        assertThat(state.getPlayers().getFirst().isConnected()).isTrue();
        assertThat(state.getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void markPlayerDisconnectedKeepsPlayerInLobby() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");

        service.markPlayerDisconnected("lobby-1", "guest");

        assertThat(store.get("lobby-1").orElseThrow().getPlayers()).hasSize(2);
    }

    @Test
    void markPlayerDisconnectedRejectsUnknownLobby() {
        assertThatThrownBy(() -> service.markPlayerDisconnected("nope", "player-1"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void markPlayerDisconnectedRejectsUnknownPlayer() {
        service.createLobby("lobby-1", "host");

        assertThatThrownBy(() -> service.markPlayerDisconnected("lobby-1", "ghost"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not in lobby");
    }

    // ========== REJOIN LOBBY ==========

    @Test
    void rejoinLobbySetsConnectedTrueAgain() {
        service.createLobby("lobby-1", "host", "client-aaa");
        service.markPlayerDisconnected("lobby-1", "host");

        GameRoomState state = service.rejoinLobby("lobby-1", "host", "client-aaa");

        assertThat(state.getPlayers().getFirst().isConnected()).isTrue();
    }

    @Test
    void rejoinLobbyAcceptsNullClientIdWhenStoredIsAlsoNull() {
        service.createLobby("lobby-1", "host");
        service.markPlayerDisconnected("lobby-1", "host");

        GameRoomState state = service.rejoinLobby("lobby-1", "host", null);

        assertThat(state.getPlayers().getFirst().isConnected()).isTrue();
    }

    @Test
    void rejoinLobbyAssignsClientIdIfPreviouslyNull() {
        service.createLobby("lobby-1", "host");
        service.markPlayerDisconnected("lobby-1", "host");

        GameRoomState state = service.rejoinLobby("lobby-1", "host", "client-new");

        assertThat(state.getPlayers().getFirst().getClientId()).isEqualTo("client-new");
    }

    @Test
    void rejoinLobbyRejectsClientIdMismatch() {
        service.createLobby("lobby-1", "host", "client-aaa");
        service.markPlayerDisconnected("lobby-1", "host");

        assertThatThrownBy(() -> service.rejoinLobby("lobby-1", "host", "client-bbb"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Client id mismatch");
    }

    @Test
    void rejoinLobbyRejectsUnknownLobby() {
        assertThatThrownBy(() -> service.rejoinLobby("nope", "player-1", "client-aaa"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejoinLobbyRejectsUnknownPlayer() {
        service.createLobby("lobby-1", "host", "client-aaa");

        assertThatThrownBy(() -> service.rejoinLobby("lobby-1", "ghost", "client-bbb"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("not in lobby");
    }

    @Test
    void rejoinLobbyIncrementsVersion() {
        service.createLobby("lobby-1", "host", "client-aaa");
        service.markPlayerDisconnected("lobby-1", "host");
        long versionBefore = store.get("lobby-1").orElseThrow().getVersion();

        GameRoomState state = service.rejoinLobby("lobby-1", "host", "client-aaa");

        assertThat(state.getVersion()).isEqualTo(versionBefore + 1);
    }

    // ========== REMOVE DISCONNECTED PLAYER ==========

    @Test
    void removeDisconnectedPlayerEvictsPlayer() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");

        LobbyLeaveResult result = service.removeDisconnectedPlayer("lobby-1", "guest");

        assertThat(result.state()).isNotNull();
        assertThat(result.state().getPlayers()).hasSize(1);
        assertThat(result.lobbyClosed()).isFalse();
    }

    @Test
    void removeDisconnectedPlayerClosesLobbyIfHost() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");

        LobbyLeaveResult result = service.removeDisconnectedPlayer("lobby-1", "host");

        assertThat(result.lobbyClosed()).isTrue();
        assertThat(store.get("lobby-1")).isEmpty();
    }

    @Test
    void removeDisconnectedPlayerReturnsEmptyForMissingLobby() {
        LobbyLeaveResult result = service.removeDisconnectedPlayer("nope", "player-1");

        assertThat(result.state()).isNull();
        assertThat(result.lobbyClosed()).isFalse();
    }

    @Test
    void removeDisconnectedPlayerReturnsEmptyForUnknownPlayer() {
        service.createLobby("lobby-1", "host");

        LobbyLeaveResult result = service.removeDisconnectedPlayer("lobby-1", "ghost");

        assertThat(result.state()).isNull();
        assertThat(result.lobbyClosed()).isFalse();
    }

    @Test
    void removeDisconnectedPlayerReturnsEmptyForBlankPlayerId() {
        service.createLobby("lobby-1", "host");

        LobbyLeaveResult result = service.removeDisconnectedPlayer("lobby-1", "");

        assertThat(result.state()).isNull();
        assertThat(result.lobbyClosed()).isFalse();
    }

    // ========== SINGLE-PLAYER-REMAINING AUTO-RESET ==========

    @Test
    void leaveLobbyResetsToLobbyPhaseWhenOnlyOnePlayerRemainsInActiveGame() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");
        service.startGame("lobby-1", 6);
        // Phase is now IN_TURN, 2 players

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "guest");

        assertThat(result.state().getPhase()).isEqualTo(GamePhase.LOBBY);
        assertThat(result.state().getPlayers()).hasSize(1);
        assertThat(result.state().getCurrentPlayerId()).isNull();
        assertThat(result.state().getLastDiceValue()).isNull();
        assertThat(result.lobbyClosed()).isFalse();
    }

    @Test
    void leaveLobbyClearsPlayerCitiesWhenResettingToLobbyPhase() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");
        service.startGame("lobby-1", 6);
        // Host has ownedCities + startCity nach startGame

        service.leaveLobby("lobby-1", "guest");

        var remainingHost = store.get("lobby-1").orElseThrow().getPlayers().getFirst();
        assertThat(remainingHost.getOwnedCities()).isEmpty();
        assertThat(remainingHost.getVisitedCities()).isEmpty();
        assertThat(remainingHost.getStartCity()).isNull();
        assertThat(remainingHost.getCurrentCity()).isNull();
    }

    @Test
    void leaveLobbyDoesNotResetWhenMultiplePlayersRemainInActiveGame() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest1");
        service.joinLobby("lobby-1", "guest2");
        service.startGame("lobby-1", 6);
        // Phase is IN_TURN, 3 players

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "guest2");

        assertThat(result.state().getPhase()).isNotEqualTo(GamePhase.LOBBY);
        assertThat(result.state().getPlayers()).hasSize(2);
    }

    @Test
    void leaveLobbyDoesNotResetWhenAlreadyInLobbyPhase() {
        service.createLobby("lobby-1", "host");
        service.joinLobby("lobby-1", "guest");
        // No startGame -> phase stays LOBBY

        LobbyLeaveResult result = service.leaveLobby("lobby-1", "guest");

        assertThat(result.state().getPhase()).isEqualTo(GamePhase.LOBBY);
        assertThat(result.state().getPlayers()).hasSize(1);
    }

    // ========== END-TO-END RECOVERY TEST ==========

    @Test
    void allPlayersAreRecoveredAfterServerRestart(@TempDir Path tempDir) {
        LobbyPersistence persistence = new LobbyPersistence(tempDir.toString());
        CityDistributor distributor = new CityDistributor();
        distributor.loadCitiesFromJson();

        // First "server run": host creates lobby, two guests join.
        InMemoryLobbyStore storeRun1 = new InMemoryLobbyStore(persistence);
        storeRun1.loadFromPersistence();
        LobbyService serviceRun1 = new LobbyService(storeRun1, distributor);

        serviceRun1.createLobby("recovery-lobby", "host-1");
        serviceRun1.joinLobby("recovery-lobby", "guest-1");
        serviceRun1.joinLobby("recovery-lobby", "guest-2");

        // Second "server run": fresh instances pointing at the same data dir.
        InMemoryLobbyStore storeRun2 = new InMemoryLobbyStore(persistence);
        storeRun2.loadFromPersistence();

        Optional<GameRoomState> recovered = storeRun2.get("recovery-lobby");
        assertThat(recovered).isPresent();
        assertThat(recovered.get().getPlayers())
                .extracting(PlayerState::getPlayerId)
                .containsExactlyInAnyOrder("host-1", "guest-1", "guest-2");
    }
}
