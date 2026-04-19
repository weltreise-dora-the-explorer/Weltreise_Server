package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests portiert von Lukas Bugelnigs GameSessionTest.kt (App).
 */
class GameSessionServiceTest {

    private GameSessionService sessionService;
    private City berlin;
    private City paris;
    private City rom;
    private PlayerState player;

    @BeforeEach
    void setup() {
        sessionService = new GameSessionService();

        berlin = new City("berlin", "Berlin", Continent.EUROPE, CityColor.RED);
        paris = new City("paris", "Paris", Continent.EUROPE, CityColor.RED);
        rom = new City("rom", "Rom", Continent.EUROPE, CityColor.RED);

        player = new PlayerState("TestPlayer");
        player.setStartCity(berlin);
    }

    @Test
    void happyPath_completeGameFlowToVictory() {
        player.getOwnedCities().add(paris);

        // 1. Start: In Berlin, Ziel Paris noch offen
        sessionService.visitCity(player, berlin);
        assertFalse(sessionService.isVictory(player), "Kein Sieg am Startpunkt ohne Ziele");

        // 2. Ziel erreichen
        sessionService.visitCity(player, paris);
        assertTrue(player.getVisitedCities().contains(paris), "Paris sollte abgehakt sein");
        assertFalse(sessionService.isVictory(player), "Ziele erreicht, aber Rückkehr zum Start fehlt");

        // 3. Sieg
        sessionService.visitCity(player, berlin);
        assertTrue(sessionService.isVictory(player), "Siegbedingung (Ziele + Startstadt) sollte erfüllt sein");
    }

    @Test
    void duplicateVisit_sameTargetDoesNotCountTwice() {
        player.getOwnedCities().add(paris);

        sessionService.visitCity(player, paris);
        sessionService.visitCity(player, paris); // Zweiter Besuch

        assertEquals(1, player.getVisitedCities().size(), "Stadt sollte nur einmal in visitedCities zählen");
    }

    @Test
    void wrongTargets_citiesNotOnOwnedListShouldNotCount() {
        player.getOwnedCities().add(paris);

        sessionService.visitCity(player, rom); // Rom ist kein Pflichtziel

        assertEquals(0, player.getVisitedCities().size(), "Nicht-Ziel-Städte dürfen nicht abgehakt werden");
        assertFalse(player.isAllTargetsReached(), "Spieler sollte noch keine Ziele erreicht haben");
    }

    @Test
    void victoryCondition_higherTargetCountForStandardMode() {
        player.getOwnedCities().add(paris);
        player.getOwnedCities().add(rom);

        // Erstes Ziel besuchen
        sessionService.visitCity(player, paris);
        sessionService.visitCity(player, berlin);
        assertFalse(sessionService.isVictory(player), "Sollte nicht gewinnen, da Rom noch fehlt");

        // Zweites Ziel besuchen
        sessionService.visitCity(player, rom);
        sessionService.visitCity(player, berlin);
        assertTrue(sessionService.isVictory(player), "Sollte gewinnen, nachdem beide Ziele besucht wurden");
    }

    @Test
    void progressStatus_correctStringRepresentation() {
        player.getOwnedCities().add(paris);
        player.getOwnedCities().add(rom);

        assertEquals("0 / 2", player.getProgressStatus());

        sessionService.visitCity(player, paris);
        assertEquals("1 / 2", player.getProgressStatus());

        sessionService.visitCity(player, rom);
        assertEquals("2 / 2", player.getProgressStatus());
    }

    @Test
    void visitCity_returnsCorrectMessages() {
        player.getOwnedCities().add(paris);

        // Ziel erreichen
        String msg1 = sessionService.visitCity(player, paris);
        assertTrue(msg1.contains("Ziel erreicht"), "Sollte 'Ziel erreicht' enthalten");

        // Nochmal besuchen
        String msg2 = sessionService.visitCity(player, paris);
        assertTrue(msg2.contains("Bereits abgehakt"), "Sollte 'Bereits abgehakt' enthalten");

        // Zwischenstopp
        String msg3 = sessionService.visitCity(player, rom);
        assertTrue(msg3.contains("Zwischenstopp"), "Sollte 'Zwischenstopp' enthalten");
    }
}
