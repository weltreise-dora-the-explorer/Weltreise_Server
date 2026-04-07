package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LobbyServiceUnitTest {

    @Test
    void joinLobbyCreatesNewLobbyAndAddsPlayer() {
        LobbyService service = new LobbyService(new InMemoryLobbyStore());

        GameRoomState state = service.joinLobby("lobby-1", "player-1");

        assertThat(state.getLobbyId()).isEqualTo("lobby-1");
        assertThat(state.getPlayers()).hasSize(1);
        assertThat(state.getPlayers().getFirst().getPlayerId()).isEqualTo("player-1");
        assertThat(state.getPhase()).isEqualTo(GamePhase.LOBBY);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void joinLobbyRejectsDuplicatePlayer() {
        LobbyService service = new LobbyService(new InMemoryLobbyStore());
        service.joinLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.joinLobby("lobby-1", "player-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already joined");
    }

    @Test
    void startGameSetsInTurnPhaseAndCurrentPlayer() {
        LobbyService service = new LobbyService(new InMemoryLobbyStore());
        service.joinLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");

        GameRoomState state = service.startGame("lobby-1");

        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(state.getLastDiceValue()).isNull();
        assertThat(state.getVersion()).isEqualTo(3L);
    }

    @Test
    void startGameRejectsLobbyWithLessThanTwoPlayers() {
        LobbyService service = new LobbyService(new InMemoryLobbyStore());
        service.joinLobby("lobby-1", "player-1");

        assertThatThrownBy(() -> service.startGame("lobby-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least two players");
    }

    @Test
    void leaveLobbyRemovesCurrentPlayerAndRotatesTurn() {
        LobbyService service = new LobbyService(new InMemoryLobbyStore());
        service.joinLobby("lobby-1", "player-1");
        service.joinLobby("lobby-1", "player-2");
        GameRoomState started = service.startGame("lobby-1");
        started.setLastDiceValue(5);

        GameRoomState state = service.leaveLobby("lobby-1", "player-1");

        assertThat(state.getPlayers()).hasSize(1);
        assertThat(state.getPlayers().getFirst().getPlayerId()).isEqualTo("player-2");
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(state.getLastDiceValue()).isNull();
        assertThat(state.getVersion()).isEqualTo(4L);
    }

    @Test
    void leaveLobbyRemovesLobbyWhenLastPlayerLeaves() {
        InMemoryLobbyStore store = new InMemoryLobbyStore();
        LobbyService service = new LobbyService(store);
        service.joinLobby("lobby-1", "player-1");

        service.leaveLobby("lobby-1", "player-1");

        assertThat(store.get("lobby-1")).isEmpty();
    }
}
