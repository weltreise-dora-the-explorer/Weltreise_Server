package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
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

    // Hier speichern wir die Städte aus der JSON dauerhaft
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

    /**
     * Wird automatisch beim Starten des Spring-Servers ausgeführt.
     * Lädt die cities.json in den Arbeitsspeicher.
     */
    @PostConstruct
    public void loadCitiesFromJson() {
        ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        TypeReference<List<City>> typeReference = new TypeReference<>() {};
        InputStream inputStream = getClass().getResourceAsStream("/cities.json");

        try {
            if (inputStream != null) {
                allCities = mapper.readValue(inputStream, typeReference);
                System.out.println("✅ Erfolgreich geladen: " + allCities.size() + " Städte stehen zur Verfügung!");
            } else {
                System.err.println("❌ cities.json nicht im resources-Ordner gefunden!");
            }
        } catch (Exception e) {
            System.err.println("❌ Fehler beim Laden der cities.json: " + e.getMessage() + " - " + e.getClass().getSimpleName());
        }
    }
    /**
     * Gibt eine Kopie aller verfügbaren Städte zurück.
     */
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
}
