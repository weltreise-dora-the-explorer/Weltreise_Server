package at.aau.serg.websocketdemoserver.game.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Spielerzustand: ID, aktuelle Position, zugeteilte Städte (Bucket List),
 * besuchte Städte und Startstadt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {
    private String playerId;
    private City startCity;
    private City currentCity;
    private int boardPosition;
    private List<City> ownedCities = new ArrayList<>();
    private List<City> visitedCities = new ArrayList<>();

    /**
     * Konstruktor nur mit playerId (für Lobby-Join).
     */
    public PlayerState(String playerId) {
        this.playerId = playerId;
    }

    /**
     * Berechnet den Fortschritt, z.B. "3 / 9".
     */
    public String getProgressStatus() {
        return visitedCities.size() + " / " + ownedCities.size();
    }

    /**
     * Gibt true zurück, wenn alle Ziele erreicht sind.
     */
    public boolean isAllTargetsReached() {
        return !ownedCities.isEmpty() && visitedCities.size() >= ownedCities.size();
    }
}
