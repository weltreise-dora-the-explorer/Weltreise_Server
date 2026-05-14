package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Verteil-Logik: Jeder Spieler bekommt eine bestimmte Anzahl an Städten
 * pro Kontinent (Stapel), um eine faire Weltreise zu garantieren.
 */
@Service
public class CityDistributor {

    private final Random random;

    private List<City> allCities = new ArrayList<>();

    public CityDistributor() {
        this(new Random());
    }

    /**
     * Konstruktor mit injizierbarem Random für testbare Zufallsverteilung.
     */
    public CityDistributor(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @PostConstruct
    public void loadCitiesFromJson() {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();

        TypeReference<List<City>> typeReference = new TypeReference<>() {};
        InputStream inputStream = getClass().getResourceAsStream("/cities.json");

        try {
            if (inputStream != null) {
                allCities = mapper.readValue(inputStream, typeReference);
            } else {
                System.err.println("cities.json not found in resources");
            }
        } catch (Exception e) {
            System.err.println("Failed to load cities.json: " + e.getMessage());
        }
    }

    public List<City> getAllCities() {
        return new ArrayList<>(allCities);
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
                        City pickedCity = currentPool.removeFirst();
                        player.getOwnedCities().add(pickedCity);
                    }
                }
            }
        }
    }

    /**
     * Verteilt Städte fair an alle Spieler, aufgeteilt nach Farbe (ORANGE, RED, GREEN).
     * Spielmodi: 6 Städte = 2 pro Farbe, 9 = 3 pro Farbe, 12 = 4 pro Farbe.
     *
     * @param allCities    Die Liste aller verfügbaren Städte.
     * @param players      Die Liste der Spieler.
     * @param amountPerColor Wie viele Städte jeder Spieler pro Farbe erhalten soll.
     */
    public void distributeByColor(List<City> allCities, List<PlayerState> players, int amountPerColor) {
        if (players.isEmpty() || allCities.isEmpty()) {
            return;
        }
        if (amountPerColor <= 0) {
            throw new IllegalArgumentException("amountPerColor must be positive");
        }

        Map<CityColor, List<City>> colorPools = allCities.stream()
                .collect(Collectors.groupingBy(City::getColor, HashMap::new, Collectors.toCollection(ArrayList::new)));

        for (List<City> pool : colorPools.values()) {
            Collections.shuffle(pool, random);
        }

        for (PlayerState player : players) {
            for (CityColor color : CityColor.values()) {
                List<City> currentPool = colorPools.get(color);
                if (currentPool != null) {
                    for (int i = 0; i < amountPerColor && !currentPool.isEmpty(); i++) {
                        player.getOwnedCities().add(currentPool.removeFirst());
                    }
                }
            }
        }
    }

    /**
     * Verteilt Städte in Runden: In jeder Runde bekommt zunächst jeder Spieler eine Stadt der
     * aktuellen Farbe, dann rotiert die Farbe (startColor → nächste → nächste → startColor …).
     * Die erste Stadt jedes Spielers hat immer startColor — das ist dessen Startstadt.
     *
     * @param allCities      Die Liste aller verfügbaren Städte.
     * @param players        Die Liste der Spieler.
     * @param amountPerColor Wie viele Städte jeder Spieler pro Farbe erhalten soll.
     * @param startColor     Die Farbe, mit der die Verteilung beginnt.
     */
    public void distributeByColorRounds(List<City> allCities, List<PlayerState> players, int amountPerColor, CityColor startColor) {
        if (players.isEmpty() || allCities.isEmpty()) {
            return;
        }
        if (amountPerColor <= 0) {
            throw new IllegalArgumentException("amountPerColor must be positive");
        }

        CityColor[] allColors = CityColor.values();
        int startIdx = startColor.ordinal();

        Map<CityColor, List<City>> colorPools = allCities.stream()
                .collect(Collectors.groupingBy(City::getColor, HashMap::new, Collectors.toCollection(ArrayList::new)));

        for (List<City> pool : colorPools.values()) {
            Collections.shuffle(pool, random);
        }

        for (int round = 0; round < amountPerColor; round++) {
            for (int offset = 0; offset < allColors.length; offset++) {
                CityColor color = allColors[(startIdx + offset) % allColors.length];
                List<City> pool = colorPools.get(color);
                if (pool == null) continue;
                for (PlayerState player : players) {
                    if (!pool.isEmpty()) {
                        player.getOwnedCities().add(pool.removeFirst());
                    }
                }
            }
        }
    }
}
