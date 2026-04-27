package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.ConnectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class WorldGraphTest {

    /** Minimales JSON mit zwei Städten und je einer Verbindung. */
    private static final String MINIMAL_JSON = """
            [
              {
                "id": "berlin",
                "name": "Berlin",
                "continent": "EUROPE",
                "color": "red",
                "trainConnections": ["paris"],
                "flightConnections": [],
                "x": 0.0, "y": 0.0, "x_relativ": 0.0, "y_relativ": 0.0
              },
              {
                "id": "paris",
                "name": "Paris",
                "continent": "EUROPE",
                "color": "blue",
                "trainConnections": [],
                "flightConnections": ["berlin"],
                "x": 0.0, "y": 0.0, "x_relativ": 0.0, "y_relativ": 0.0
              }
            ]
            """;

    private static final String JSON_WITH_UNKNOWN_FIELDS = """
            [
              {
                "id": "tokio",
                "name": "Tokio",
                "continent": "ASIA",
                "color": "orange",
                "trainConnections": [],
                "flightConnections": [],
                "x": 1.0, "y": 2.0, "x_relativ": 0.1, "y_relativ": 0.2
              }
            ]
            """;

    private static final String JSON_INVALID_CONTINENT = """
            [
              {
                "id": "mars",
                "name": "Mars",
                "continent": "MARS",
                "color": "red",
                "trainConnections": [],
                "flightConnections": [],
                "x": 0.0, "y": 0.0, "x_relativ": 0.0, "y_relativ": 0.0
              }
            ]
            """;

    private WorldGraph graph;

    @BeforeEach
    void setup() {
        graph = new WorldGraph(MINIMAL_JSON);
    }

    @Test
    void getCityById_knownId_returnsCity() {
        CityNode berlin = graph.getCityById("berlin");
        assertNotNull(berlin);
        assertEquals("Berlin", berlin.getName());
    }

    @Test
    void getCityById_unknownId_returnsNull() {
        assertNull(graph.getCityById("xyz"));
    }

    @Test
    void getAllCities_returnsCorrectCount() {
        Collection<CityNode> cities = graph.getAllCities();
        assertEquals(2, cities.size());
    }

    @Test
    void trainConnection_isLinkedCorrectly() {
        CityNode berlin = graph.getCityById("berlin");
        assertNotNull(berlin);

        boolean hasTrainToParis = berlin.getConnections().stream()
                .anyMatch(c -> c.getDestination().getId().equals("paris")
                        && c.getType() == ConnectionType.TRAIN);

        assertTrue(hasTrainToParis, "Berlin sollte eine Zugverbindung nach Paris haben");
    }

    @Test
    void flightConnection_isLinkedCorrectly() {
        CityNode paris = graph.getCityById("paris");
        assertNotNull(paris);

        boolean hasFlightToBerlin = paris.getConnections().stream()
                .anyMatch(c -> c.getDestination().getId().equals("berlin")
                        && c.getType() == ConnectionType.FLIGHT);

        assertTrue(hasFlightToBerlin, "Paris sollte eine Flugverbindung nach Berlin haben");
    }

    @Test
    void cityWithNoConnections_hasEmptyConnectionList() {
        WorldGraph g = new WorldGraph(JSON_WITH_UNKNOWN_FIELDS);
        CityNode tokio = g.getCityById("tokio");
        assertNotNull(tokio);
        assertTrue(tokio.getConnections().isEmpty());
    }

    @Test
    void cityWithInvalidContinent_isSkipped() {
        WorldGraph g = new WorldGraph(JSON_INVALID_CONTINENT);
        assertTrue(g.getAllCities().isEmpty(), "Stadt mit unbekanntem Kontinent sollte übersprungen werden");
    }

    @Test
    void emptyJson_producesEmptyGraph() {
        WorldGraph g = new WorldGraph("[]");
        assertTrue(g.getAllCities().isEmpty());
    }

    @Test
    void getCityById_returnsCorrectContinent() {
        CityNode berlin = graph.getCityById("berlin");
        assertNotNull(berlin);
        assertEquals("EUROPE", berlin.getContinent().name());
    }
}
