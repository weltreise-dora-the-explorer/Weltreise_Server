package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ClientCommand;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameOverMessage;
import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.messaging.dtos.GoalReachedMessage;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.Connection;
import at.aau.serg.websocketdemoserver.game.models.ConnectionType;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalAndGameOverUnitTest {

    @Mock
    private MovementEngine movementEngine;

    @Mock
    private WorldGraph worldGraph;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GameSessionService gameSessionService;
    private GameCommandService service;

    private static final String LOBBY_ID = "lobby-1";
    private static final String PLAYER_ID = "player-1";

    @BeforeEach
    void setUp() {
        gameSessionService = new GameSessionService();
        service = new GameCommandService(new Random(), worldGraph, movementEngine, gameSessionService, messagingTemplate);
    }

    // ===== Goal-reached tests =====

    @Test
    void moveToBucketListCity_marksGoalAndBroadcasts() {
        City vienna = city("vienna", "Vienna");
        City paris = city("paris", "Paris");

        CityNode viennaNode = cityNode("vienna", "Vienna");
        CityNode parisNode = cityNode("paris", "Paris");

        PlayerState player = playerAt(vienna);
        player.setStartCity(vienna);
        player.setRemainingSteps(1);
        player.getOwnedCities().add(paris);

        when(worldGraph.getCityById("vienna")).thenReturn(viennaNode);
        when(movementEngine.getValidOptions(any(), any(), anyInt(), any()))
                .thenReturn(List.of(new Connection(parisNode, ConnectionType.TRAIN)));

        GameRoomState state = inTurnState(List.of(player));
        state.setLastDiceValue(1);

        service.processCommand(state, moveToCityCommand("paris"));

        assertThat(player.getCurrentCity().getId()).isEqualTo("paris");
        assertThat(player.getVisitedCities()).isEmpty();
        assertThat(state.getPhase()).isEqualTo(GamePhase.MINIGAME);

        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/goal-reached"), any(GoalReachedMessage.class));
    }

    @Test
    void moveToNonTargetCity_noGoalBroadcast() {
        City vienna = city("vienna", "Vienna");
        City berlin = city("berlin", "Berlin");
        City paris = city("paris", "Paris");

        CityNode viennaNode = cityNode("vienna", "Vienna");
        CityNode berlinNode = cityNode("berlin", "Berlin");

        PlayerState player = playerAt(vienna);
        player.setRemainingSteps(1);
        player.getOwnedCities().add(paris);

        when(worldGraph.getCityById("vienna")).thenReturn(viennaNode);
        when(movementEngine.getValidOptions(any(), any(), anyInt(), any()))
                .thenReturn(List.of(new Connection(berlinNode, ConnectionType.TRAIN)));

        GameRoomState state = inTurnState(List.of(player));
        state.setLastDiceValue(1);

        service.processCommand(state, moveToCityCommand("berlin"));

        assertThat(player.getVisitedCities()).isEmpty();
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(GoalReachedMessage.class));
    }

    @Test
    void moveToAlreadyVisitedTarget_noSecondBroadcast() {
        City vienna = city("vienna", "Vienna");
        City paris = city("paris", "Paris");

        CityNode viennaNode = cityNode("vienna", "Vienna");
        CityNode parisNode = cityNode("paris", "Paris");

        PlayerState player = playerAt(vienna);
        player.setStartCity(vienna);
        player.setRemainingSteps(1);
        player.getOwnedCities().add(paris);
        player.getVisitedCities().add(paris);

        when(worldGraph.getCityById("vienna")).thenReturn(viennaNode);
        when(movementEngine.getValidOptions(any(), any(), anyInt(), any()))
                .thenReturn(List.of(new Connection(parisNode, ConnectionType.TRAIN)));

        GameRoomState state = inTurnState(List.of(player));
        state.setLastDiceValue(1);

        service.processCommand(state, moveToCityCommand("paris"));

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(GoalReachedMessage.class));
    }

    // ===== Game-over triggered by visiting last target that is also startCity =====

    @Test
    void visitLastTargetAndAtStartCity_triggersGameOver() {
        City vienna = city("vienna", "Vienna");

        CityNode someNode = cityNode("other", "Other");
        CityNode viennaNode = cityNode("vienna", "Vienna");

        PlayerState player = playerAt(city("other", "Other"));
        player.setStartCity(vienna);
        player.setRemainingSteps(1);
        player.getOwnedCities().add(vienna);

        when(worldGraph.getCityById("other")).thenReturn(someNode);
        when(movementEngine.getValidOptions(any(), any(), anyInt(), any()))
                .thenReturn(List.of(new Connection(viennaNode, ConnectionType.TRAIN)));

        PlayerState player2 = new PlayerState("player-2");
        player2.setCurrentCity(city("berlin", "Berlin"));
        player2.setStartCity(city("berlin", "Berlin"));

        GameRoomState state = inTurnState(List.of(player, player2));
        state.setLastDiceValue(1);

        service.processCommand(state, moveToCityCommand("vienna"));

        assertThat(state.getPhase()).isEqualTo(GamePhase.MINIGAME);
        assertThat(state.isGameOver()).isFalse();
        assertThat(player.getVisitedCities()).isEmpty();

        ClientCommand finishCommand = new ClientCommand(
                CommandType.FINISH_MINIGAME,
                LOBBY_ID,
                PLAYER_ID,
                null,
                null
        );
        finishCommand.setWinnerPlayerId(PLAYER_ID);

        service.processCommand(state, finishCommand);

        assertThat(player.getVisitedCities()).extracting(City::getId).containsExactly("vienna");
        assertThat(state.isGameOver()).isTrue();

        ArgumentCaptor<GameOverMessage> captor = ArgumentCaptor.forClass(GameOverMessage.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/game-over"), captor.capture());

        GameOverMessage gameOverMsg = captor.getValue();
        assertThat(gameOverMsg.getScores()).hasSize(2);
        assertThat(gameOverMsg.getScores()).extracting(s -> s.getPlayerName()).contains(PLAYER_ID);
    }

    // ===== Game-over triggered by returning home after all goals already visited =====

    @Test
    void returnToStartAfterAllGoalsDone_triggersGameOver() {
        City vienna = city("vienna", "Vienna");
        City paris = city("paris", "Paris");

        CityNode parisNode = cityNode("paris", "Paris");
        CityNode viennaNode = cityNode("vienna", "Vienna");

        PlayerState player = playerAt(paris);
        player.setStartCity(vienna);
        player.setRemainingSteps(1);
        player.getOwnedCities().add(paris);
        player.getVisitedCities().add(paris);

        when(worldGraph.getCityById("paris")).thenReturn(parisNode);
        when(movementEngine.getValidOptions(any(), any(), anyInt(), any()))
                .thenReturn(List.of(new Connection(viennaNode, ConnectionType.TRAIN)));

        GameRoomState state = inTurnState(List.of(player));
        state.setLastDiceValue(1);

        service.processCommand(state, moveToCityCommand("vienna"));

        assertThat(state.isGameOver()).isTrue();
        verify(messagingTemplate).convertAndSend(
                eq("/topic/game-over"), any(GameOverMessage.class));
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/goal-reached"), any(GoalReachedMessage.class));
    }

    @Test
    void gameOverNotTriggeredWhenNotAllGoalsDone() {
        City vienna = city("vienna", "Vienna");
        City paris = city("paris", "Paris");
        City berlin = city("berlin", "Berlin");

        CityNode parisNode = cityNode("paris", "Paris");
        CityNode viennaNode = cityNode("vienna", "Vienna");

        PlayerState player = playerAt(paris);
        player.setStartCity(vienna);
        player.setRemainingSteps(1);
        player.getOwnedCities().add(paris);
        player.getOwnedCities().add(berlin);
        player.getVisitedCities().add(paris);

        when(worldGraph.getCityById("paris")).thenReturn(parisNode);
        when(movementEngine.getValidOptions(any(), any(), anyInt(), any()))
                .thenReturn(List.of(new Connection(viennaNode, ConnectionType.TRAIN)));

        GameRoomState state = inTurnState(List.of(player));
        state.setLastDiceValue(1);

        service.processCommand(state, moveToCityCommand("vienna"));

        assertThat(state.isGameOver()).isFalse();
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/game-over"), any(GameOverMessage.class));
    }

    // ===== Movement blocked after game over =====

    @Test
    void rollDiceBlockedAfterGameOver() {
        GameRoomState state = inTurnState(List.of(playerAt(city("vienna", "Vienna"))));
        state.setGameOver(true);

        assertThatThrownBy(() -> service.processCommand(state,
                new ClientCommand(CommandType.ROLL_DICE, LOBBY_ID, PLAYER_ID, null, null)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("beendet");
    }

    @Test
    void moveToCityBlockedAfterGameOver() {
        GameRoomState state = inTurnState(List.of(playerAt(city("vienna", "Vienna"))));
        state.setGameOver(true);
        state.setLastDiceValue(3);

        assertThatThrownBy(() -> service.processCommand(state, moveToCityCommand("paris")))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("beendet");
    }

    // ===== Score calculation =====

    @Test
    void calculateScore_allReached() {
        PlayerState player = new PlayerState(PLAYER_ID);
        player.getOwnedCities().add(city("paris", "Paris"));
        player.getOwnedCities().add(city("berlin", "Berlin"));
        player.getVisitedCities().add(city("paris", "Paris"));
        player.getVisitedCities().add(city("berlin", "Berlin"));

        assertThat(service.calculateScore(player)).isEqualTo(2);
    }

    @Test
    void calculateScore_noneReached() {
        PlayerState player = new PlayerState(PLAYER_ID);
        player.getOwnedCities().add(city("paris", "Paris"));
        player.getOwnedCities().add(city("berlin", "Berlin"));

        assertThat(service.calculateScore(player)).isEqualTo(-2);
    }

    @Test
    void calculateScore_halfReached() {
        PlayerState player = new PlayerState(PLAYER_ID);
        player.getOwnedCities().add(city("paris", "Paris"));
        player.getOwnedCities().add(city("berlin", "Berlin"));
        player.getVisitedCities().add(city("paris", "Paris"));

        assertThat(service.calculateScore(player)).isEqualTo(0);
    }

    // ===== Helpers =====

    private City city(String id, String name) {
        return new City(id, name, Continent.EUROPE_AFRICA, CityColor.RED);
    }

    private CityNode cityNode(String id, String name) {
        return new CityNode(id, name, Continent.EUROPE_AFRICA, CityColor.RED);
    }

    private PlayerState playerAt(City city) {
        PlayerState player = new PlayerState(PLAYER_ID);
        player.setCurrentCity(city);
        return player;
    }

    private ClientCommand moveToCityCommand(String targetCityId) {
        return new ClientCommand(CommandType.MOVE_TO_CITY, LOBBY_ID, PLAYER_ID, null, null, targetCityId, null, null, null);
    }

    private GameRoomState inTurnState(List<PlayerState> players) {
        GameRoomState state = new GameRoomState();
        state.setLobbyId(LOBBY_ID);
        state.setPlayers(new ArrayList<>(players));
        state.setPhase(GamePhase.IN_TURN);
        state.setCurrentPlayerId(PLAYER_ID);
        return state;
    }
}
