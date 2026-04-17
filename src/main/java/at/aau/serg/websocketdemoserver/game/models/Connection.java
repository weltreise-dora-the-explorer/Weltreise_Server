package at.aau.serg.websocketdemoserver.game.models;

/**
 * Verbindung zwischen zwei Städten (Zug oder Flug).
 * Portiert von Lukas Bugelnigs Connection.kt (App).
 */
public class Connection {
    private final CityNode destination;
    private final ConnectionType type;

    public Connection(CityNode destination, ConnectionType type) {
        this.destination = destination;
        this.type = type;
    }

    public CityNode getDestination() {
        return destination;
    }

    public ConnectionType getType() {
        return type;
    }
}
