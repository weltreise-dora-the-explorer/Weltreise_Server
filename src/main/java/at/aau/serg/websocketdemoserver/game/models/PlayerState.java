package at.aau.serg.websocketdemoserver.game.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Spielerzustand: ID, aktuelle Position, und zugeteilte Städte (Bucket List).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {
    private String playerId;
    private String currentCity;
    private int boardPosition;
    private List<City> ownedCities = new ArrayList<>();

    /**
     * Konstruktor nur mit playerId (für Lobby-Join).
     */
    public PlayerState(String playerId) {
        this.playerId = playerId;
    }
}
