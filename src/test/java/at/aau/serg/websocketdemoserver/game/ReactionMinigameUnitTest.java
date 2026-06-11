package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionMinigameUnitTest {

    // READY TESTS

    @Test
    void reactionReadyAddsPlayerAndSetsReadyTimeout() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());

        service.processCommand(state, readyCommand("player-1"));

        assertThat(state.getReactionReadyPlayerIds()).containsExactly("player-1");
        assertThat(state.getReactionReadyEndsAtMs()).isNotNull();
        assertThat(state.getReactionStartTimeMs()).isNull();
        assertThat(state.getReactionButtonVisibleAtMs()).isNull();
        assertThat(state.getReactionRoundEndsAtMs()).isNull();
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void reactionReadyStartsRoundWhenAllPlayersAreReady() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());

        service.processCommand(state, readyCommand("player-1"));
        service.processCommand(state, readyCommand("player-2"));

        assertThat(state.getReactionReadyPlayerIds()).containsExactly("player-1", "player-2");
        assertThat(state.getReactionStartTimeMs()).isNotNull();
        assertThat(state.getReactionButtonVisibleAtMs()).isNotNull();
        assertThat(state.getReactionRoundEndsAtMs()).isNotNull();
        assertThat(state.getReactionButtonVisibleAtMs()).isGreaterThan(state.getReactionStartTimeMs());
        assertThat(state.getReactionRoundEndsAtMs()).isGreaterThan(state.getReactionButtonVisibleAtMs());
        assertThat(state.getVersion()).isEqualTo(2L);
    }

    // PRESS TESTS

    @Test
    void reactionPressStoresReactionTimeAfterButtonIsVisible() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());
        state.setReactionButtonVisibleAtMs(System.currentTimeMillis() - 100);

        service.processCommand(state, pressCommand("player-1"));

        assertThat(state.getReactionPressTimesMs()).containsKey("player-1");
        assertThat(state.getReactionPressTimesMs().get("player-1")).isGreaterThanOrEqualTo(0L);
        assertThat(state.getMinigameWinnerPlayerId()).isNull();
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void reactionPressSetsFastestPlayerAsWinnerWhenAllPlayersPressed() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());

        state.setReactionButtonVisibleAtMs(System.currentTimeMillis() - 500);
        state.getReactionPressTimesMs().put("player-1", 400L);

        service.processCommand(state, pressCommand("player-2"));

        assertThat(state.getReactionPressTimesMs()).containsKeys("player-1", "player-2");
        assertThat(state.getMinigameWinnerPlayerId()).isEqualTo("player-1");
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void reactionPressRejectsTooEarlyPress() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());
        state.setReactionButtonVisibleAtMs(System.currentTimeMillis() + 10_000);

        assertThatThrownBy(() -> service.processCommand(state, pressCommand("player-1")))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("too early");
    }

    // RESET TESTS

    @Test
    void startMinigameResetsPreviousReactionState() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();
        PlayerState player = players.getFirst();
        player.getOwnedCities().add(player.getCurrentCity());

        GameRoomState state = inTurnState(players);
        state.getReactionReadyPlayerIds().add("player-1");
        state.setReactionReadyEndsAtMs(123L);
        state.setReactionStartTimeMs(456L);
        state.setReactionButtonVisibleAtMs(789L);
        state.setReactionRoundEndsAtMs(999L);
        state.getReactionPressTimesMs().put("player-1", 111L);
        state.setMinigameWinnerPlayerId("player-1");

        service.processCommand(state, new ClientCommand(CommandType.START_MINIGAME, "lobby-1", "player-1", null, null));

        assertThat(state.getPhase()).isEqualTo(GamePhase.MINIGAME);
        assertThat(state.getReactionReadyPlayerIds()).isEmpty();
        assertThat(state.getReactionReadyEndsAtMs()).isNull();
        assertThat(state.getReactionStartTimeMs()).isNull();
        assertThat(state.getReactionButtonVisibleAtMs()).isNull();
        assertThat(state.getReactionRoundEndsAtMs()).isNull();
        assertThat(state.getReactionPressTimesMs()).isEmpty();
        assertThat(state.getMinigameWinnerPlayerId()).isNull();
    }

    // ========== TIMEOUT TESTS ==========

    @Test
    void reactionReadyTimeoutStartsRoundEvenWhenNotAllPlayersAreReady() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());

        state.setReactionReadyEndsAtMs(System.currentTimeMillis() - 100);

        service.processCommand(state, readyCommand("player-1"));

        assertThat(state.getReactionStartTimeMs()).isNotNull();
        assertThat(state.getReactionButtonVisibleAtMs()).isNotNull();
        assertThat(state.getReactionRoundEndsAtMs()).isNotNull();
        assertThat(state.getReactionReadyPlayerIds()).containsExactly("player-1");
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void reactionPressTimeoutAssignsMissingPlayersSlowTimeAndSelectsWinner() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());

        state.setReactionButtonVisibleAtMs(System.currentTimeMillis() - 70_000);
        state.setReactionRoundEndsAtMs(System.currentTimeMillis() - 1_000);
        state.getReactionPressTimesMs().put("player-1", 350L);

        service.processCommand(state, pressCommand("player-2"));

        assertThat(state.getReactionPressTimesMs().get("player-1")).isEqualTo(350L);
        assertThat(state.getReactionPressTimesMs().get("player-2")).isEqualTo(60_000L);
        assertThat(state.getMinigameWinnerPlayerId()).isEqualTo("player-1");
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    // INVALID PHASE TESTS

    @Test
    void reactionReadyRejectsWhenNotInMinigamePhase() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());

        assertThatThrownBy(() -> service.processCommand(state, readyCommand("player-1")))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("minigame phase");
    }

    @Test
    void reactionPressRejectsWhenNotInMinigamePhase() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());

        assertThatThrownBy(() -> service.processCommand(state, pressCommand("player-1")))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("minigame phase");
    }

    // DUPLICATE PRESS TESTS

    @Test
    void reactionPressIgnoresDuplicatePress() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = minigameState(defaultPlayers());

        state.setReactionButtonVisibleAtMs(System.currentTimeMillis() - 500);
        state.getReactionPressTimesMs().put("player-1", 300L);

        service.processCommand(state, pressCommand("player-1"));

        assertThat(state.getReactionPressTimesMs().get("player-1")).isEqualTo(300L);
        assertThat(state.getReactionPressTimesMs()).hasSize(1);
        assertThat(state.getVersion()).isEqualTo(0L);
    }

    // HELPERS

    private GameRoomState minigameState(List<PlayerState> players) {
        GameRoomState state = inTurnState(players);
        state.setPhase(GamePhase.MINIGAME);
        return state;
    }

    private GameRoomState inTurnState(List<PlayerState> players) {
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setPlayers(players);
        state.setPhase(GamePhase.IN_TURN);
        state.setCurrentPlayerId("player-1");
        return state;
    }

    private ClientCommand readyCommand(String playerId) {
        return new ClientCommand(CommandType.REACTION_READY, "lobby-1", playerId, null, null);
    }

    private ClientCommand pressCommand(String playerId) {
        return new ClientCommand(CommandType.REACTION_PRESS, "lobby-1", playerId, null, null);
    }

    private List<PlayerState> defaultPlayers() {
        List<PlayerState> players = new ArrayList<>();

        PlayerState p1 = new PlayerState("player-1");
        p1.setCurrentCity(new City("vienna", "Vienna", Continent.EUROPE_AFRICA, CityColor.RED));
        players.add(p1);

        PlayerState p2 = new PlayerState("player-2");
        p2.setCurrentCity(new City("paris", "Paris", Continent.EUROPE_AFRICA, CityColor.GREEN));
        players.add(p2);

        return players;
    }

    private static class FixedRandom extends Random {
        private final int value;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}