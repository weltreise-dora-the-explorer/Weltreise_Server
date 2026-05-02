package at.aau.serg.websocketdemoserver.game.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityNodeTest {

    private CityNode berlin;
    private CityNode paris;

    @BeforeEach
    void setup() {
        berlin = new CityNode("berlin", "Berlin", Continent.EUROPE_AFRICA, CityColor.RED);
        paris  = new CityNode("paris",  "Paris",  Continent.EUROPE_AFRICA, CityColor.ORANGE);
    }

    @Test
    void getters_returnCorrectValues() {
        assertEquals("berlin",       berlin.getId());
        assertEquals("Berlin",       berlin.getName());
        assertEquals(Continent.EUROPE_AFRICA, berlin.getContinent());
        assertEquals(CityColor.RED,   berlin.getColor());
    }

    @Test
    void newCityNode_hasNoConnections() {
        assertTrue(berlin.getConnections().isEmpty());
    }

    @Test
    void addConnection_increasesConnectionCount() {
        berlin.addConnection(paris, ConnectionType.TRAIN);
        assertEquals(1, berlin.getConnections().size());
    }

    @Test
    void addConnection_storesCorrectDestinationAndType() {
        berlin.addConnection(paris, ConnectionType.FLIGHT);
        Connection conn = berlin.getConnections().getFirst();

        assertEquals("paris",           conn.getDestination().getId());
        assertEquals(ConnectionType.FLIGHT, conn.getType());
    }

    @Test
    void addMultipleConnections_allAreStored() {
        CityNode london = new CityNode("london", "London", Continent.EUROPE_AFRICA, CityColor.GREEN);
        berlin.addConnection(paris,  ConnectionType.TRAIN);
        berlin.addConnection(london, ConnectionType.FLIGHT);

        assertEquals(2, berlin.getConnections().size());
    }
}
