package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.Connection;
import at.aau.serg.websocketdemoserver.game.models.ConnectionType;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovementEngineTest {

    private MovementEngine engine;

    private CityNode berlin;
    private CityNode paris;
    private CityNode tokio;

    @BeforeEach
    void setup() {
        engine = new MovementEngine();

        berlin = new CityNode("berlin", "Berlin", Continent.EUROPE, CityColor.RED);
        paris  = new CityNode("paris",  "Paris",  Continent.EUROPE, CityColor.ORANGE);
        CityNode london = new CityNode("london", "London", Continent.EUROPE, CityColor.GREEN);
        tokio  = new CityNode("tokio",  "Tokio",  Continent.ASIA,   CityColor.ORANGE);

        // Berlin --(Zug)--> Paris, Berlin --(Flug)--> Tokio, Berlin --(Zug)--> London
        berlin.addConnection(paris,  ConnectionType.TRAIN);
        berlin.addConnection(tokio,  ConnectionType.FLIGHT);
        berlin.addConnection(london, ConnectionType.TRAIN);
    }

    // ── getValidOptions ──────────────────────────────────────────────────

    @Test
    void getValidOptions_noPoints_returnsEmpty() {
        List<Connection> options = engine.getValidOptions(berlin, null, 0, null);
        assertTrue(options.isEmpty());
    }

    @Test
    void getValidOptions_enoughPointsForTrain_includesTrain() {
        List<Connection> options = engine.getValidOptions(berlin, null, 1, null);
        assertTrue(options.stream().anyMatch(c -> c.getDestination().getId().equals("paris")));
        assertTrue(options.stream().anyMatch(c -> c.getDestination().getId().equals("london")));
    }

    @Test
    void getValidOptions_notEnoughPointsForFlight_excludesFlight() {
        // 1 Punkt reicht nicht für Flug (kostet 2)
        List<Connection> options = engine.getValidOptions(berlin, null, 1, null);
        assertFalse(options.stream().anyMatch(c -> c.getDestination().getId().equals("tokio")),
                "Flug nach Tokio sollte ohne ausreichend Punkte nicht erlaubt sein");
    }

    @Test
    void getValidOptions_enoughPointsForFlight_includesFlight() {
        List<Connection> options = engine.getValidOptions(berlin, null, 2, null);
        assertTrue(options.stream().anyMatch(c -> c.getDestination().getId().equals("tokio")));
    }

    @Test
    void getValidOptions_uTurnForbidden_excludesPreviousCity() {
        // Von Berlin nach Paris, dann zurück — Paris sollte als Vorherige gesperrt sein
        List<Connection> options = engine.getValidOptions(berlin, paris, 2, null);
        assertFalse(options.stream().anyMatch(c -> c.getDestination().getId().equals("paris")),
                "U-Turn nach Paris sollte verboten sein");
    }

    @Test
    void getValidOptions_uTurnForbiddenOnlyForPrevious_otherCitiesAllowed() {
        List<Connection> options = engine.getValidOptions(berlin, paris, 2, null);
        // London und Tokio sind weiterhin erlaubt
        assertTrue(options.stream().anyMatch(c -> c.getDestination().getId().equals("london")));
        assertTrue(options.stream().anyMatch(c -> c.getDestination().getId().equals("tokio")));
    }

    @Test
    void getValidOptions_finalDestReachableEvenWithoutEnoughPoints_isIncluded() {
        // Nur 1 Punkt, aber Tokio ist finale Zielstadt (Flug kostet 2) → trotzdem erlaubt
        List<Connection> options = engine.getValidOptions(berlin, null, 1, tokio);
        assertTrue(options.stream().anyMatch(c -> c.getDestination().getId().equals("tokio")),
                "Finale Zielstadt sollte auch ohne ausreichend Punkte erreichbar sein");
    }

    @Test
    void getValidOptions_noPreviousCity_noUTurnFiltering() {
        List<Connection> options = engine.getValidOptions(berlin, null, 2, null);
        assertEquals(3, options.size(), "Alle drei Verbindungen sollten erlaubt sein");
    }

    // ── executeStep ──────────────────────────────────────────────────────

    @Test
    void executeStep_trainCost_reducesPointsByOne() {
        Connection trainConn = berlin.getConnections().stream()
                .filter(c -> c.getType() == ConnectionType.TRAIN)
                .findFirst().orElseThrow();

        int remaining = engine.executeStep(3, trainConn, false);
        assertEquals(2, remaining);
    }

    @Test
    void executeStep_flightCost_reducesPointsByTwo() {
        Connection flightConn = berlin.getConnections().stream()
                .filter(c -> c.getType() == ConnectionType.FLIGHT)
                .findFirst().orElseThrow();

        int remaining = engine.executeStep(3, flightConn, false);
        assertEquals(1, remaining);
    }

    @Test
    void executeStep_finalDestinationWithNotEnoughPoints_returnsZeroNotNegative() {
        Connection flightConn = berlin.getConnections().stream()
                .filter(c -> c.getType() == ConnectionType.FLIGHT)
                .findFirst().orElseThrow();

        // 1 Punkt, Flug kostet 2 → Sonderregel: 0 statt -1
        int remaining = engine.executeStep(1, flightConn, true);
        assertEquals(0, remaining, "Finale Zielstadt: Restpunkte sollen auf 0 fallen, nicht negativ");
    }

    @Test
    void executeStep_finalDestinationWithEnoughPoints_normalDeduction() {
        Connection trainConn = berlin.getConnections().stream()
                .filter(c -> c.getType() == ConnectionType.TRAIN)
                .findFirst().orElseThrow();

        int remaining = engine.executeStep(3, trainConn, true);
        assertEquals(2, remaining);
    }

    @Test
    void executeStep_zeroPointsAfterMove_returnsZero() {
        Connection trainConn = berlin.getConnections().stream()
                .filter(c -> c.getType() == ConnectionType.TRAIN)
                .findFirst().orElseThrow();

        int remaining = engine.executeStep(1, trainConn, false);
        assertEquals(0, remaining);
    }
}
