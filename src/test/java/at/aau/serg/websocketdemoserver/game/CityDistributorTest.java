package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
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
                new City("wien", "Wien", Continent.EUROPE, CityColor.RED),
                new City("berlin", "Berlin", Continent.EUROPE, CityColor.RED),
                new City("paris", "Paris", Continent.EUROPE, CityColor.ORANGE),
                new City("rom", "Rom", Continent.EUROPE, CityColor.ORANGE),
                new City("madrid", "Madrid", Continent.EUROPE, CityColor.GREEN),
                new City("london", "London", Continent.EUROPE, CityColor.GREEN),
                // Asien (6)
                new City("tokio", "Tokio", Continent.ASIA, CityColor.RED),
                new City("peking", "Peking", Continent.ASIA, CityColor.RED),
                new City("bangkok", "Bangkok", Continent.ASIA, CityColor.ORANGE),
                new City("seoul", "Seoul", Continent.ASIA, CityColor.ORANGE),
                new City("neu-delhi", "Neu-Delhi", Continent.ASIA, CityColor.GREEN),
                new City("singapur", "Singapur", Continent.ASIA, CityColor.GREEN),
                // Nordamerika (6)
                new City("new-york", "New York", Continent.NORTH_AMERICA, CityColor.RED),
                new City("los-angeles", "Los Angeles", Continent.NORTH_AMERICA, CityColor.RED),
                new City("toronto", "Toronto", Continent.NORTH_AMERICA, CityColor.ORANGE),
                new City("chicago", "Chicago", Continent.NORTH_AMERICA, CityColor.ORANGE),
                new City("mexiko-stadt", "Mexiko-Stadt", Continent.NORTH_AMERICA, CityColor.GREEN),
                new City("miami", "Miami", Continent.NORTH_AMERICA, CityColor.GREEN),
                // Südamerika (6)
                new City("rio", "Rio de Janeiro", Continent.SOUTH_AMERICA, CityColor.RED),
                new City("buenos-aires", "Buenos Aires", Continent.SOUTH_AMERICA, CityColor.RED),
                new City("lima", "Lima", Continent.SOUTH_AMERICA, CityColor.ORANGE),
                new City("bogota", "Bogota", Continent.SOUTH_AMERICA, CityColor.ORANGE),
                new City("santiago", "Santiago", Continent.SOUTH_AMERICA, CityColor.GREEN),
                new City("quito", "Quito", Continent.SOUTH_AMERICA, CityColor.GREEN)
        ));
    }

    @Test
    void distributeByContinent_givesCorrectAmountForMultiplePlayers() {
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
        List<City> fewCities = new ArrayList<>(List.of(
                new City("wien", "Wien", Continent.EUROPE, CityColor.RED)
        ));

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

        List<City> allAssigned = new ArrayList<>();
        for (PlayerState player : players) {
            allAssigned.addAll(player.getOwnedCities());
        }

        long uniqueCount = allAssigned.stream().distinct().count();
        assertEquals(allAssigned.size(), uniqueCount, "Keine Stadt darf doppelt vergeben werden");
    }

    // ========== distributeByColor Tests ==========

    private List<City> colorTestCities() {
        List<City> cities = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            cities.add(new City("orange-" + i, "Orange " + i, Continent.EUROPE, CityColor.ORANGE));
            cities.add(new City("red-" + i, "Red " + i, Continent.ASIA, CityColor.RED));
            cities.add(new City("green-" + i, "Green " + i, Continent.NORTH_AMERICA, CityColor.GREEN));
        }
        return cities;
    }

    @Test
    void distributeByColor_givesCorrectAmountPerColorForMultiplePlayers() {
        distributor.distributeByColor(colorTestCities(), players, 2);

        for (PlayerState player : players) {
            assertEquals(6, player.getOwnedCities().size(),
                    player.getPlayerId() + " sollte genau 6 Städte haben");

            long orangeCount = player.getOwnedCities().stream().filter(c -> c.getColor() == CityColor.ORANGE).count();
            long redCount    = player.getOwnedCities().stream().filter(c -> c.getColor() == CityColor.RED).count();
            long greenCount  = player.getOwnedCities().stream().filter(c -> c.getColor() == CityColor.GREEN).count();

            assertEquals(2, orangeCount, player.getPlayerId() + " hat nicht 2 ORANGE");
            assertEquals(2, redCount,    player.getPlayerId() + " hat nicht 2 RED");
            assertEquals(2, greenCount,  player.getPlayerId() + " hat nicht 2 GREEN");
        }
    }

    @Test
    void distributeByColor_noCityAssignedTwice() {
        distributor.distributeByColor(colorTestCities(), players, 2);

        List<City> allAssigned = new ArrayList<>();
        for (PlayerState player : players) {
            allAssigned.addAll(player.getOwnedCities());
        }

        long uniqueCount = allAssigned.stream().distinct().count();
        assertEquals(allAssigned.size(), uniqueCount, "Keine Stadt darf doppelt vergeben werden");
    }

    @Test
    void distributeByColor_handlesNotEnoughCitiesInPool() {
        List<City> fewCities = new ArrayList<>(List.of(
                new City("orange-1", "Orange 1", Continent.EUROPE, CityColor.ORANGE)
        ));

        distributor.distributeByColor(fewCities, players, 2);

        assertEquals(1, players.get(0).getOwnedCities().size());
        assertEquals(0, players.get(1).getOwnedCities().size());
        assertEquals(0, players.get(2).getOwnedCities().size());
    }

    @Test
    void distributeByColor_handlesEmptyCityList() {
        distributor.distributeByColor(Collections.emptyList(), players, 2);

        for (PlayerState player : players) {
            assertEquals(0, player.getOwnedCities().size());
        }
    }

    @Test
    void distributeByColor_throwsOnInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                distributor.distributeByColor(colorTestCities(), players, 0));
        assertThrows(IllegalArgumentException.class, () ->
                distributor.distributeByColor(colorTestCities(), players, -1));
    }

    // ========== distributeByColorRounds Tests ==========

    @Test
    void distributeByColorRounds_firstCityOfEachPlayerHasStartColor() {
        for (CityColor startColor : CityColor.values()) {
            players.forEach(p -> p.getOwnedCities().clear());
            distributor.distributeByColorRounds(colorTestCities(), players, 2, startColor);

            for (PlayerState player : players) {
                assertEquals(startColor, player.getOwnedCities().getFirst().getColor(),
                        player.getPlayerId() + " erste Stadt soll " + startColor + " sein");
            }
        }
    }

    @Test
    void distributeByColorRounds_startCitiesAreUniqueAcrossPlayers() {
        distributor.distributeByColorRounds(colorTestCities(), players, 2, CityColor.RED);

        List<City> startCities = players.stream()
                .map(p -> p.getOwnedCities().getFirst())
                .toList();

        long unique = startCities.stream().distinct().count();
        assertEquals(startCities.size(), unique, "Jede Startstadt muss eindeutig sein");
    }

    @Test
    void distributeByColorRounds_givesCorrectAmountPerColor() {
        distributor.distributeByColorRounds(colorTestCities(), players, 2, CityColor.ORANGE);

        for (PlayerState player : players) {
            assertEquals(6, player.getOwnedCities().size(),
                    player.getPlayerId() + " sollte genau 6 Städte haben");

            long orangeCount = player.getOwnedCities().stream().filter(c -> c.getColor() == CityColor.ORANGE).count();
            long redCount    = player.getOwnedCities().stream().filter(c -> c.getColor() == CityColor.RED).count();
            long greenCount  = player.getOwnedCities().stream().filter(c -> c.getColor() == CityColor.GREEN).count();

            assertEquals(2, orangeCount, player.getPlayerId() + " hat nicht 2 ORANGE");
            assertEquals(2, redCount,    player.getPlayerId() + " hat nicht 2 RED");
            assertEquals(2, greenCount,  player.getPlayerId() + " hat nicht 2 GREEN");
        }
    }

    @Test
    void distributeByColorRounds_noCityAssignedTwice() {
        distributor.distributeByColorRounds(colorTestCities(), players, 2, CityColor.GREEN);

        List<City> allAssigned = new ArrayList<>();
        for (PlayerState player : players) {
            allAssigned.addAll(player.getOwnedCities());
        }

        long uniqueCount = allAssigned.stream().distinct().count();
        assertEquals(allAssigned.size(), uniqueCount, "Keine Stadt darf doppelt vergeben werden");
    }

    @Test
    void distributeByColorRounds_throwsOnInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                distributor.distributeByColorRounds(colorTestCities(), players, 0, CityColor.RED));
        assertThrows(IllegalArgumentException.class, () ->
                distributor.distributeByColorRounds(colorTestCities(), players, -1, CityColor.RED));
    }

    @Test
    void distributeByColorRounds_handlesEmptyCityList() {
        distributor.distributeByColorRounds(Collections.emptyList(), players, 2, CityColor.RED);

        for (PlayerState player : players) {
            assertEquals(0, player.getOwnedCities().size());
        }
    }
}
