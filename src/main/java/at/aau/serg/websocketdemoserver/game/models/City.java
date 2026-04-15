package at.aau.serg.websocketdemoserver.game.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Repräsentiert eine Stadt auf dem Spielfeld.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class City {
    private String id;
    private String name;
    private Continent continent;
    private CityColor color;
}
