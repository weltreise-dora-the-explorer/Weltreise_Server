package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Verteil-Logik: Jeder Spieler bekommt eine bestimmte Anzahl an Städten
 * pro Kontinent (Stapel), um eine faire Weltreise zu garantieren.
 */
@Service
public class CityDistributor {

    private final Random random;

    public CityDistributor() {
        this(new Random());
    }

    /**
     * Konstruktor mit injizierbarem Random für testbare Zufallsverteilung.
     */
    public CityDistributor(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Verteilt Städte fair an alle Spieler, aufgeteilt nach Kontinenten.
     *
     * @param allCities          Die Liste aller verfügbaren Städte (ungemischt).
     * @param players            Die Liste der Spieler.
     * @param amountPerContinent Wie viele Städte jeder Spieler AUS JEDEM Kontinent erhalten soll.
     */
    public void distributeByContinent(List<City> allCities, List<PlayerState> players, int amountPerContinent) {
        if (players.isEmpty() || allCities.isEmpty()) {
            return;
        }
        if (amountPerContinent <= 0) {
            throw new IllegalArgumentException("amountPerContinent must be positive");
        }

        // 1. Gruppiere alle Städte nach Kontinent und mische die einzelnen Stapel
        Map<Continent, List<City>> continentPools = allCities.stream()
                .collect(Collectors.groupingBy(City::getContinent, HashMap::new, Collectors.toCollection(ArrayList::new)));

        // Stapel mischen
        for (List<City> pool : continentPools.values()) {
            Collections.shuffle(pool, random);
        }

        // 2. Gehe jeden Spieler durch
        for (PlayerState player : players) {
            // 3. Gehe jeden Kontinent-Stapel durch
            for (Continent continent : Continent.values()) {
                List<City> currentPool = continentPools.get(continent);
                if (currentPool != null) {
                    // Ziehe n Karten für diesen Spieler aus diesem Kontinent
                    for (int i = 0; i < amountPerContinent && !currentPool.isEmpty(); i++) {
                        City pickedCity = currentPool.remove(0);
                        player.getOwnedCities().add(pickedCity);
                    }
                }
            }
        }
    }
}
