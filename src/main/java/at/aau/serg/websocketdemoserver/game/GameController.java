package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Erstellt und initialisiert eine neue Spielsession für einen Spieler.
 * Portiert von Lukas Bugelnigs GameController.kt (App).
 */
public class GameController {

    /**
     * Erzeugt einen spielbereiten PlayerState: Zielstädte werden zufällig verteilt,
     * die erste Karte wird als Startstadt gesetzt.
     *
     * @param playerName Name des Spielers.
     * @param allCities  Alle verfügbaren Städte.
     * @param mode       Gewählter Spielmodus (bestimmt Anzahl der Zielstädte).
     * @return Initialisierter PlayerState, bereit zum Spielen.
     */
    public PlayerState createSession(String playerName, List<City> allCities, GameMode mode) {
        PlayerState player = new PlayerState(playerName);

        List<City> shuffled = new ArrayList<>(allCities);
        Collections.shuffle(shuffled);

        int count = Math.min(mode.getRequiredTargets(), shuffled.size());
        List<City> assigned = new ArrayList<>(shuffled.subList(0, count));

        player.setOwnedCities(assigned);

        if (!assigned.isEmpty()) {
            player.setStartCity(assigned.get(0));
            player.setCurrentCity(assigned.get(0));
        }

        return player;
    }
}
