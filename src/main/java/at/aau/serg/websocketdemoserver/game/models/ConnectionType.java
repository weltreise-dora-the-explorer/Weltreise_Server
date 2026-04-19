package at.aau.serg.websocketdemoserver.game.models;

/**
 * Reisearten mit ihren Bewegungskosten.
 * Portiert von Lukas Bugelnigs ConnectionType.kt (App).
 */
public enum ConnectionType {
    TRAIN(1),
    FLIGHT(2);

    private final int cost;

    ConnectionType(int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }
}
