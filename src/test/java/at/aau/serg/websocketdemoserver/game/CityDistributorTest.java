package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CityDistributorTest {

    private CityDistributor distributor;
    private List<PlayerState> players;
    private List<City> testCities;

    @BeforeEach
    void setup() {
        // Fester Seed damit Tests deterministisch sind
        distributor = new CityDistributor(new Random(42));

        // 3 Spieler für Test
        players = new ArrayList<>(List.of(
                new PlayerState("Alice"),
                new PlayerState("Bob"),
                new PlayerState("Charlie")
        ));

        // Liste: 4 Kontinente mit je 6 Städten
        testCities = new ArrayList<>(List.of(
                // Europa (6)
                new City("Wien", Continent.EUROPE), new City("Berlin", Continent.EUROPE),
                new City("Paris", Continent.EUROPE), new City("Rom", Continent.EUROPE),
                new City("Madrid", Continent.EUROPE), new City("London", Continent.EUROPE),
                // Asien (6)
                new City("Tokio", Continent.ASIA), new City("Peking", Continent.ASIA),
                new City("Bangkok", Continent.ASIA), new City("Seoul", Continent.ASIA),
                new City("Neu-Delhi", Continent.ASIA), new City("Singapur", Continent.ASIA),
                // Nordamerika (6)
                new City("New York", Continent.NORTH_AMERICA), new City("Los Angeles", Continent.NORTH_AMERICA),
                new City("Toronto", Continent.NORTH_AMERICA), new City("Chicago", Continent.NORTH_AMERICA),
                new City("Mexiko-Stadt", Continent.NORTH_AMERICA), new City("Miami", Continent.NORTH_AMERICA),
                // Südamerika (6)
                new City("Rio de Janeiro", Continent.SOUTH_AMERICA), new City("Buenos Aires", Continent.SOUTH_AMERICA),
                new City("Lima", Continent.SOUTH_AMERICA), new City("Bogota", Continent.SOUTH_AMERICA),
                new City("Santiago", Continent.SOUTH_AMERICA), new City("Quito", Continent.SOUTH_AMERICA)
        ));
    }

    @Test
    void distributeByContinent_givesCorrectAmountForMultiplePlayers() {
        // Normalfall: Jeder zieht 2 Karten pro Kontinent
        distributor.distributeByContinent(testCities, players, 2);

        for (PlayerState player : players) {
            assertEquals(8, player.getOwnedCities().size(),
                    player.getPlayerId() + " sollte genau 8 Städte haben");

            long europeCount = player.getOwnedCities().stream()
                    .filter(c -> c.getContinent() == Continent.EUROPE).count();
            long asiaCount = player.getOwnedCities().stream()
                    .filter(c -> c.getContinent() == Continent.ASIA).count();
            long naCount = player.getOwnedCities().stream()
                    .filter(c -> c.getContinent() == Continent.NORTH_AMERICA).count();
            long saCount = player.getOwnedCities().stream()
                    .filter(c -> c.getContinent() == Continent.SOUTH_AMERICA).count();

            assertEquals(2, europeCount, player.getPlayerId() + " hat nicht 2 in Europa");
            assertEquals(2, asiaCount, player.getPlayerId() + " hat nicht 2 in Asien");
            assertEquals(2, naCount, player.getPlayerId() + " hat nicht 2 in Nordamerika");
            assertEquals(2, saCount, player.getPlayerId() + " hat nicht 2 in Südamerika");
        }
    }

    @Test
    void distributeByContinent_handlesNotEnoughCitiesInPool() {
        // Randfall: Zu wenige Städte
        List<City> fewCities = new ArrayList<>(List.of(new City("Wien", Continent.EUROPE)));

        distributor.distributeByContinent(fewCities, players, 2);

        assertEquals(1, players.get(0).getOwnedCities().size(),
                "Alice sollte die einzige verfügbare Stadt bekommen");
        assertEquals(0, players.get(1).getOwnedCities().size(),
                "Bob sollte leer ausgehen");
        assertEquals(0, players.get(2).getOwnedCities().size(),
                "Charlie sollte leer ausgehen");
    }

    @Test
    void distributeByContinent_handlesEmptyCityListWithoutCrashing() {
        // Randfall: Gar keine Städte
        List<City> emptyCities = Collections.emptyList();

        distributor.distributeByContinent(emptyCities, players, 2);

        for (PlayerState player : players) {
            assertEquals(0, player.getOwnedCities().size(),
                    player.getPlayerId() + " sollte keine Städte haben");
        }
    }

    @Test
    void distributeByContinent_handlesEmptyPlayerList() {
        List<PlayerState> noPlayers = Collections.emptyList();

        // Sollte nicht crashen
        distributor.distributeByContinent(testCities, noPlayers, 2);
    }

    @Test
    void distributeByContinent_throwsOnInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                distributor.distributeByContinent(testCities, players, 0));
        assertThrows(IllegalArgumentException.class, () ->
                distributor.distributeByContinent(testCities, players, -1));
    }

    @Test
    void distributeByContinent_noCityAssignedTwice() {
        distributor.distributeByContinent(testCities, players, 2);

        // Alle zugeteilten Städte sammeln
        List<City> allAssigned = new ArrayList<>();
        for (PlayerState player : players) {
            allAssigned.addAll(player.getOwnedCities());
        }

        // Keine Duplikate
        long uniqueCount = allAssigned.stream().distinct().count();
        assertEquals(allAssigned.size(), uniqueCount, "Keine Stadt darf doppelt vergeben werden");
    }
}
