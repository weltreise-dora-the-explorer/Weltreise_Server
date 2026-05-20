package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameMode;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameCommandServiceUnitTest {

    @Test
    void rollDiceSetsDiceValueAndIncrementsVersion() {
        GameCommandService service = new GameCommandService(new FixedRandom(4));
        GameRoomState state = inTurnState(defaultPlayers());

        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));

        assertThat(state.getLastDiceValue()).isEqualTo(5);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void rollDiceRejectsNonActivePlayer() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-2", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Not your turn");
    }

    @Test
    void moveTokenRejectsWhenDiceWasNotRolled() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 3, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Roll dice before moving");
    }

    @Test
    void moveTokenRejectsWhenStepsDoNotMatchDiceValue() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState(defaultPlayers());
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 2, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Move steps must match dice value");
    }

    @Test
    void moveTokenAdvancesPositionRotatesTurnAndClearsDice() {
        GameCommandService service = new GameCommandService(new FixedRandom(2));
        List<PlayerState> players = defaultPlayers();
        GameRoomState state = inTurnState(players);
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));

        service.processCommand(state, new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", 3, null));

        assertThat(players.getFirst().getBoardPosition()).isEqualTo(3);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(state.getLastDiceValue()).isNull();
        assertThat(state.getVersion()).isEqualTo(2L);
    }

    @Test
    void moveTokenRejectsNonActivePlayer() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-2", 2, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Not your turn");
    }

    @Test
    void rollDiceRejectsWhenDiceAlreadyRolled() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState(defaultPlayers());
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Dice already rolled");
    }

    @Test
    void processCommandRejectsNullCommand() {
        GameCommandService service = new GameCommandService();

        assertThatThrownBy(() -> service.processCommand(new GameRoomState(), null))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("required");
    }

    @Test
    void processCommandRejectsNullState() {
        GameCommandService service = new GameCommandService();
        ClientCommand command = new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null);

        assertThatThrownBy(() -> service.processCommand(null, command))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("required");
    }

    @Test
    void processCommandRejectsWrongPhase() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = new GameRoomState();
        state.setPhase(GamePhase.LOBBY);
        state.setPlayers(defaultPlayers());
        state.setCurrentPlayerId("player-1");

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("current phase");
    }

    @Test
    void moveTokenRejectsWhenMoveStepsNull() {
        GameCommandService service = new GameCommandService(new FixedRandom(3));
        GameRoomState state = inTurnState(defaultPlayers());
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.MOVE_TOKEN, "lobby-1", "player-1", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Move steps are required");
    }

    @Test
    void hostCanUpdateGameModeInLobbyPhase() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));

        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setHostId("host-1");
        state.setPhase(GamePhase.LOBBY);
        state.setGameMode(GameMode.CITY_HOPPER);
        state.setVersion(0L);

        ClientCommand command = new ClientCommand(CommandType.UPDATE_GAME_MODE, "lobby-1", "host-1", null, null);
        command.setGameMode(GameMode.GRAND_TOUR);

        service.processCommand(state, command);

        assertThat(state.getGameMode()).isEqualTo(GameMode.GRAND_TOUR);
        assertThat(state.getGameMode().getStops()).isEqualTo(9);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void nonHostCannotUpdateGameMode() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));

        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setHostId("host-1");
        state.setPhase(GamePhase.LOBBY);
        state.setGameMode(GameMode.CITY_HOPPER);
        state.setVersion(0L);

        ClientCommand command = new ClientCommand(CommandType.UPDATE_GAME_MODE, "lobby-1", "player-2", null, null);
        command.setGameMode(GameMode.EPIC_VOYAGE);

        assertThatThrownBy(() -> service.processCommand(state, command))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Only the host can change the game mode");

        assertThat(state.getGameMode()).isEqualTo(GameMode.CITY_HOPPER);
        assertThat(state.getVersion()).isEqualTo(0L);
    }

    @Test
    void startMinigameSetsPhaseToMinigameAndIncrementsVersion() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();
        PlayerState player = players.getFirst();
        player.getOwnedCities().add(player.getCurrentCity());

        GameRoomState state = inTurnState(players);

        service.processCommand(state, new ClientCommand(CommandType.START_MINIGAME, "lobby-1", "player-1", null, null));

        assertThat(state.getPhase()).isEqualTo(GamePhase.MINIGAME);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void startMinigameRejectsWhenCurrentCityIsNotTargetCity() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.START_MINIGAME, "lobby-1", "player-1", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("target city");
    }

    @Test
    void startMinigameRejectsAlreadyCompletedTargetCity() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();
        PlayerState player = players.getFirst();
        player.getOwnedCities().add(player.getCurrentCity());
        player.getVisitedCities().add(player.getCurrentCity());

        GameRoomState state = inTurnState(players);

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.START_MINIGAME, "lobby-1", "player-1", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void finishMinigameAddsCurrentTargetCityToVisitedCitiesAndReturnsToTurn() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();
        PlayerState targetPlayer = players.getFirst();
        targetPlayer.getOwnedCities().add(targetPlayer.getCurrentCity());

        GameRoomState state = inTurnState(players);
        state.setPhase(GamePhase.MINIGAME);

        ClientCommand command = new ClientCommand(
                CommandType.FINISH_MINIGAME,
                "lobby-1",
                "player-1",
                null,
                null
        );
        command.setWinnerPlayerId("player-1");

        service.processCommand(state, command);

        assertThat(targetPlayer.getVisitedCities()).containsExactly(targetPlayer.getCurrentCity());
        assertThat(targetPlayer.getFreePassCount()).isZero();
        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void finishMinigameGivesVoucherToOtherWinner() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();

        PlayerState targetPlayer = players.getFirst();
        PlayerState winner = players.get(1);

        City lostCity = targetPlayer.getCurrentCity();
        targetPlayer.getOwnedCities().add(lostCity);

        GameRoomState state = inTurnState(players);
        state.setPhase(GamePhase.MINIGAME);

        ClientCommand command = new ClientCommand(
                CommandType.FINISH_MINIGAME,
                "lobby-1",
                "player-1",
                null,
                null
        );
        command.setWinnerPlayerId("player-2");

        service.processCommand(state, command);

        assertThat(targetPlayer.getVisitedCities()).isEmpty();
        assertThat(winner.getFreePassCount()).isEqualTo(1);

        assertThat(targetPlayer.getOwnedCities()).hasSize(1);
        assertThat(targetPlayer.getOwnedCities())
                .noneMatch(city -> city.getId().equals(lostCity.getId()));

        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void finishMinigameKeepsCurrentPlayerWhenStepsRemain() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();

        PlayerState targetPlayer = players.getFirst();
        targetPlayer.getOwnedCities().add(targetPlayer.getCurrentCity());
        targetPlayer.setRemainingSteps(2);

        GameRoomState state = inTurnState(players);
        state.setPhase(GamePhase.MINIGAME);
        state.setCurrentPlayerId("player-1");
        state.setLastDiceValue(4);

        ClientCommand command = new ClientCommand(
                CommandType.FINISH_MINIGAME,
                "lobby-1",
                "player-1",
                null,
                null
        );
        command.setWinnerPlayerId("player-1");

        service.processCommand(state, command);

        assertThat(targetPlayer.getVisitedCities()).containsExactly(targetPlayer.getCurrentCity());
        assertThat(targetPlayer.getRemainingSteps()).isEqualTo(2);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-1");
        assertThat(state.getLastDiceValue()).isEqualTo(4);
        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void finishMinigameMovesToNextPlayerWhenNoStepsRemain() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();

        PlayerState targetPlayer = players.getFirst();
        targetPlayer.getOwnedCities().add(targetPlayer.getCurrentCity());
        targetPlayer.setRemainingSteps(0);

        GameRoomState state = inTurnState(players);
        state.setPhase(GamePhase.MINIGAME);
        state.setCurrentPlayerId("player-1");
        state.setLastDiceValue(4);

        ClientCommand command = new ClientCommand(
                CommandType.FINISH_MINIGAME,
                "lobby-1",
                "player-1",
                null,
                null
        );
        command.setWinnerPlayerId("player-1");

        service.processCommand(state, command);

        assertThat(targetPlayer.getVisitedCities()).containsExactly(targetPlayer.getCurrentCity());
        assertThat(targetPlayer.getRemainingSteps()).isEqualTo(0);
        assertThat(state.getCurrentPlayerId()).isEqualTo("player-2");
        assertThat(state.getLastDiceValue()).isNull();
        assertThat(state.getValidMoveIds()).isEmpty();
        assertThat(state.getPhase()).isEqualTo(GamePhase.IN_TURN);
        assertThat(state.getVersion()).isEqualTo(1L);
    }

    @Test
    void finishMinigameRejectsWhenNotInMinigamePhase() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        GameRoomState state = inTurnState(defaultPlayers());

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.FINISH_MINIGAME, "lobby-1", "player-1", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("during minigame phase");
    }

    @Test
    void finishMinigameRejectsNonCurrentPlayer() {
        GameCommandService service = new GameCommandService(new FixedRandom(1));
        List<PlayerState> players = defaultPlayers();
        PlayerState player = players.getFirst();
        player.getOwnedCities().add(player.getCurrentCity());

        GameRoomState state = inTurnState(players);
        state.setPhase(GamePhase.MINIGAME);

        assertThatThrownBy(() -> service.processCommand(
                state,
                new ClientCommand(CommandType.FINISH_MINIGAME, "lobby-1", "player-2", null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("current target player");
    }


    @Test
    void previousCityIdClearedAfterAutoTurnSwitch_allowsReturnInNextTurn() {
        // Single-player game so the auto-switch immediately returns to player-1.
        // novosibirsk –train(1)– omsk: rolling 1 uses up all steps and triggers auto-switch.
        GameCommandService service = new GameCommandService(new FixedRandom(0)); // dice always = 1
        PlayerState p1 = new PlayerState("player-1");
        p1.setCurrentCity(new City("novosibirsk", "Novosibirsk", Continent.ASIA, CityColor.ORANGE));
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setPlayers(new ArrayList<>(List.of(p1)));
        state.setPhase(GamePhase.IN_TURN);
        state.setCurrentPlayerId("player-1");

        // Turn 1: roll 1, move novosibirsk → omsk (cost=1, steps hit 0 → auto-switch)
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));
        ClientCommand moveCmd = new ClientCommand(CommandType.MOVE_TO_CITY, "lobby-1", "player-1", null, null, "omsk", null, null, null);
        service.processCommand(state, moveCmd);

        // Auto-switch returns to player-1 (only player). previousCityId must be null now.
        assertThat(p1.getPreviousCityId()).isNull();

        // Turn 2: roll again — novosibirsk must appear in validMoveIds (no U-turn block)
        service.processCommand(state, new ClientCommand(CommandType.ROLL_DICE, "lobby-1", "player-1", null, null));
        assertThat(state.getValidMoveIds()).contains("novosibirsk");
    }

    @Test
    void endTurnClearsValidMoveIds() {
        GameCommandService service = new GameCommandService(new FixedRandom(2));
        GameRoomState state = inTurnState(defaultPlayers());
        state.setLastDiceValue(3);
        state.getPlayers().getFirst().setRemainingSteps(3);
        state.setValidMoveIds(new ArrayList<>(List.of("some-city-id")));

        service.processCommand(state, new ClientCommand(CommandType.END_TURN, "lobby-1", "player-1", null, null));

        assertThat(state.getValidMoveIds()).isEmpty();
    }

    private GameRoomState inTurnState(List<PlayerState> players) {
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setPlayers(players);
        state.setPhase(GamePhase.IN_TURN);
        state.setCurrentPlayerId("player-1");
        return state;
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
