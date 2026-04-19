package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.Connection;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Berechnet erlaubte Züge und Bewegungskosten.
 * Portiert von Lukas Bugelnigs MovementEngine.kt (App).
 */
public class MovementEngine {

    /**
     * Liefert alle legalen Verbindungen von der aktuellen Stadt.
     *
     * @param current        Aktuelle Stadt.
     * @param previous       Vorherige Stadt (U-Turns sind verboten).
     * @param remainingPoints Verbleibende Würfelpunkte.
     * @param finalDest      Finale Zielstadt des Spielers (darf auch ohne ausreichend Punkte betreten werden).
     */
    public List<Connection> getValidOptions(CityNode current, CityNode previous,
                                            int remainingPoints, CityNode finalDest) {
        if (remainingPoints <= 0) return List.of();

        return current.getConnections().stream()
                .filter(conn -> {
                    // U-Turn im selben Zug verboten
                    if (previous != null && conn.getDestination().getId().equals(previous.getId()))
                        return false;

                    boolean isFinal = finalDest != null
                            && conn.getDestination().getId().equals(finalDest.getId());
                    boolean hasPoints = remainingPoints >= conn.getType().getCost();

                    return hasPoints || isFinal;
                })
                .collect(Collectors.toList());
    }

    /**
     * Zieht die Reisekosten ab. Sonderregel: Beim finalen Zielort dürfen
     * Restpunkte verfallen (Rückgabe 0 statt negativem Wert).
     */
    public int executeStep(int currentPoints, Connection connection, boolean isFinalDestination) {
        int remaining = currentPoints - connection.getType().getCost();
        if (isFinalDestination && remaining < 0) return 0;
        return remaining;
    }
}
