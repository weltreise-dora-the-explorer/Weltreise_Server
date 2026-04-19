package at.aau.serg.websocketdemoserver.game.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Erweitert City um Verbindungen (Zug/Flug) für den WorldGraph.
 * Wird nur vom CLI verwendet — City.java bleibt unverändert.
 * Portiert von Lukas Bugelnigs City.kt (App).
 */
public class CityNode {
    private final String id;
    private final String name;
    private final Continent continent;
    private final CityColor color;
    private final List<Connection> connections = new ArrayList<>();

    public CityNode(String id, String name, Continent continent, CityColor color) {
        this.id = id;
        this.name = name;
        this.continent = continent;
        this.color = color;
    }

    public void addConnection(CityNode destination, ConnectionType type) {
        connections.add(new Connection(destination, type));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Continent getContinent() { return continent; }
    public CityColor getColor() { return color; }
    public List<Connection> getConnections() { return connections; }
}
