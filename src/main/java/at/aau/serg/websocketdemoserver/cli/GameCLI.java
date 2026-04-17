package at.aau.serg.websocketdemoserver.cli;

import at.aau.serg.websocketdemoserver.game.GameController;
import at.aau.serg.websocketdemoserver.game.GameMode;
import at.aau.serg.websocketdemoserver.game.GameSessionService;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone CLI zum Testen der Spiellogik ohne laufenden Server.
 * Starten: java -cp <classpath> at.aau.serg.websocketdemoserver.cli.GameCLI
 */
public class GameCLI {

    public static void main(String[] args) throws Exception {
        List<City> cities = loadCities();
        if (cities.isEmpty()) {
            System.out.println("Fehler: Keine Städte geladen. cities.json prüfen.");
            return;
        }

        Scanner scanner = new Scanner(System.in);

        System.out.print("Spielername: ");
        String playerName = scanner.nextLine().trim();

        GameMode mode = selectGameMode(scanner);

        GameController controller = new GameController();
        PlayerState player = controller.createSession(playerName, cities, mode);
        GameSessionService service = new GameSessionService();

        printGameStart(player, mode);
        runGameLoop(scanner, player, service, cities);

        scanner.close();
    }

    private static List<City> loadCities() throws Exception {
        InputStream in = GameCLI.class.getClassLoader().getResourceAsStream("cities.json");
        if (in == null) {
            throw new IllegalStateException("cities.json nicht in resources gefunden");
        }
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

        List<City> cities = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{([^{}]+)\\}", Pattern.DOTALL);
        Pattern fieldPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");

        Matcher objectMatcher = objectPattern.matcher(json);
        while (objectMatcher.find()) {
            String block = objectMatcher.group(1);
            Map<String, String> fields = new HashMap<>();
            Matcher fieldMatcher = fieldPattern.matcher(block);
            while (fieldMatcher.find()) {
                fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
            }
            if (!fields.containsKey("id") || !fields.containsKey("name")
                    || !fields.containsKey("continent") || !fields.containsKey("color")) {
                continue;
            }
            try {
                cities.add(new City(
                        fields.get("id"),
                        fields.get("name"),
                        Continent.valueOf(fields.get("continent")),
                        CityColor.valueOf(fields.get("color").toUpperCase())
                ));
            } catch (IllegalArgumentException ignored) {
                // Unbekannter Kontinent oder Farbe — Stadt überspringen
            }
        }
        return cities;
    }

    private static GameMode selectGameMode(Scanner scanner) {
        GameMode[] modes = GameMode.values();
        System.out.println("\nSpielmodi:");
        for (int i = 0; i < modes.length; i++) {
            System.out.printf("  [%d] %-15s (%d Ziele)%n", i + 1, modes[i].name(), modes[i].getRequiredTargets());
        }
        System.out.print("Wähle Modus (1-" + modes.length + "): ");
        String input = scanner.nextLine().trim();
        int index;
        try {
            index = Integer.parseInt(input) - 1;
        } catch (NumberFormatException e) {
            index = 0;
        }
        return modes[Math.max(0, Math.min(index, modes.length - 1))];
    }

    private static void printGameStart(PlayerState player, GameMode mode) {
        System.out.println("\n=== Weltreise gestartet! ===");
        System.out.println("Modus   : " + mode.name());
        System.out.println("Startort: " + player.getStartCity().getName());
        System.out.println("Deine Zielstädte:");
        player.getOwnedCities().forEach(c ->
                System.out.println("  - " + c.getName() + " (" + c.getContinent() + ")")
        );
        System.out.println("\nTipp: Stadtname oder ID eingeben. 'liste' zeigt alle Städte. 'quit' beendet.\n");
    }

    private static void runGameLoop(Scanner scanner, PlayerState player, GameSessionService service, List<City> cities) {
        while (true) {
            System.out.printf("[%s] Stadt: ", player.getProgressStatus());
            String input = scanner.nextLine().trim();

            if ("quit".equalsIgnoreCase(input)) {
                System.out.println("Spiel beendet.");
                break;
            }

            if ("liste".equalsIgnoreCase(input)) {
                cities.forEach(c -> System.out.println("  " + c.getId() + " -> " + c.getName()));
                continue;
            }

            City city = findCity(cities, input);
            if (city == null) {
                System.out.println("Unbekannte Stadt: \"" + input + "\". Versuche ID oder vollen Namen.");
                continue;
            }

            System.out.println(service.visitCity(player, city));

            if (service.isVictory(player)) {
                System.out.println("\nGlückwunsch, " + player.getPlayerId() + "! Weltreise abgeschlossen!");
                break;
            }
        }
    }

    private static City findCity(List<City> cities, String input) {
        return cities.stream()
                .filter(c -> c.getName().equalsIgnoreCase(input) || c.getId().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }
}
