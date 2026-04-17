package at.aau.serg.websocketdemoserver.game.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionTypeTest {

    @Test
    void train_costIsOne() {
        assertEquals(1, ConnectionType.TRAIN.getCost());
    }

    @Test
    void flight_costIsTwo() {
        assertEquals(2, ConnectionType.FLIGHT.getCost());
    }

    @Test
    void flight_costIsHigherThanTrain() {
        assertTrue(ConnectionType.FLIGHT.getCost() > ConnectionType.TRAIN.getCost());
    }

    @Test
    void values_containsExactlyTwoTypes() {
        assertEquals(2, ConnectionType.values().length);
    }
}
