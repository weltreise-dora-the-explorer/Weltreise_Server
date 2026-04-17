package at.aau.serg.websocketdemoserver.cli;

import at.aau.serg.websocketdemoserver.game.GameMode;
import at.aau.serg.websocketdemoserver.game.MovementEngine;
import at.aau.serg.websocketdemoserver.game.WorldGraph;
import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.Connection;
import at.aau.serg.websocketdemoserver.game.models.ConnectionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * Standalone CLI zum Testen der vollständigen Spiellogik ohne laufenden Server.
 * Spiegelt 1:1 die FakeConsoleActivity aus der App (feature/basic-game-logic):
 *   - Würfeln pro Runde (1-6 Punkte)
 *   - Bewegung entlang echter Verbindungen (Zug=1 Pkt, Flug=2 Pkt)
 *   - U-Turn-Verbot im selben Zug
 *   - Sieg: alle Zielstädte besucht UND zurück zur Startstadt
 *
 * Starten: java -cp <classpath> at.aau.serg.websocketdemoserver.cli.GameCLI
 */
public class GameCLI {

    private static final Random DICE = new Random();

    public static void main(String[] args) throws Exception {
        WorldGraph graph = WorldGraph.loadFromResources();
        if (graph.getAllCities().isEmpty()) {
            System.out.println("Fehler: Keine Städte geladen. cities.json prüfen.");
            return;
        }

        Scanner scanner = new Scanner(System.in);

        System.out.print("Spielername: ");
        String playerName = scanner.nextLine().trim();

        GameMode mode = selectGameMode(scanner);

        // Spieler-Setup: Zielstädte zufällig zuteilen, erste = Startstadt
        List<CityNode> allCities = new ArrayList<>(graph.getAllCities());
        Collections.shuffle(allCities);
        int count = Math.min(mode.getRequiredTargets(), allCities.size());
        List<CityNode> targets = new ArrayList<>(allCities.subList(0, count));

        CityNode startCity   = targets.get(0);
        CityNode currentCity = startCity;
        CityNode previousCity = null;

        List<CityNode> visitedTargets = new ArrayList<>();

        MovementEngine engine = new MovementEngine();

        printGameStart(playerName, mode, startCity, targets);

        int round = 1;

        // ── Hauptschleife ──────────────────────────────────────────────────
        gameLoop:
        while (true) {
            int points = rollDice();
            System.out.println("\n--- RUNDE " + round + " ---");
            System.out.println("Gewürfelt: " + points + " Punkte");

            CityNode prevInRound = null; // U-Turn-Tracking innerhalb der Runde

            // ── Zugrunde: Spieler bewegt sich, bis Punkte aufgebraucht ────
            while (points > 0) {
                // Nächstes nicht abgehaktes Ziel als "finale Destination"
                CityNode finalDest = targets.stream()
                        .filter(t -> visitedTargets.stream().noneMatch(v -> v.getId().equals(t.getId())))
                        .findFirst()
                        .orElse(null);

                List<Connection> options = engine.getValidOptions(currentCity, prevInRound, points, finalDest);

                System.out.println("\nOrt: " + currentCity.getName()
                        + " [" + currentCity.getContinent() + "]"
                        + "  |  Punkte: " + points);
                System.out.println("Fortschritt: " + visitedTargets.size() + " / " + targets.size());

                if (options.isEmpty()) {
                    System.out.println("Keine Züge mehr möglich. Runde endet.");
                    break;
                }

                System.out.println("Optionen:");
                for (int i = 0; i < options.size(); i++) {
                    Connection c = options.get(i);
                    System.out.printf("  [%d] %-20s (%s, %d Pkt)%n",
                            i + 1,
                            c.getDestination().getName(),
                            c.getType() == ConnectionType.TRAIN ? "Zug" : "Flug",
                            c.getType().getCost());
                }
                System.out.println("  [0] Runde beenden");
                System.out.print("> ");

                String input = scanner.nextLine().trim();

                if ("quit".equalsIgnoreCase(input)) {
                    System.out.println("Spiel beendet.");
                    break gameLoop;
                }

                if ("0".equals(input)) {
                    System.out.println("Runde beendet.");
                    break;
                }

                int choice;
                try {
                    choice = Integer.parseInt(input) - 1;
                } catch (NumberFormatException e) {
                    System.out.println("Ungültige Eingabe.");
                    continue;
                }

                if (choice < 0 || choice >= options.size()) {
                    System.out.println("Ungültige Auswahl.");
                    continue;
                }

                Connection chosen = options.get(choice);
                boolean isFinalDest = finalDest != null
                        && chosen.getDestination().getId().equals(finalDest.getId());

                prevInRound  = currentCity;
                previousCity = currentCity;
                currentCity  = chosen.getDestination();
                points       = engine.executeStep(points, chosen, isFinalDest);

                System.out.println("Reise nach " + currentCity.getName() + " angetreten!");

                // Ziel abgehakt?
                final String arrivedId = currentCity.getId();
                boolean isTarget    = targets.stream().anyMatch(t -> t.getId().equals(arrivedId));
                boolean alreadyDone = visitedTargets.stream().anyMatch(v -> v.getId().equals(arrivedId));

                if (isTarget && !alreadyDone) {
                    visitedTargets.add(currentCity);
                    System.out.println("Ziel erreicht: " + currentCity.getName()
                            + "! Stand: " + visitedTargets.size() + " / " + targets.size());
                } else if (isTarget) {
                    System.out.println("Bereits abgehakt: " + currentCity.getName());
                } else {
                    System.out.println("Zwischenstopp: " + currentCity.getName());
                }

                // Siegprüfung: alle Ziele + zurück an Startstadt
                boolean allDone    = visitedTargets.size() >= targets.size();
                boolean backAtHome = currentCity.getId().equals(startCity.getId());

                if (allDone && backAtHome) {
                    System.out.println("\nGlückwunsch, " + playerName
                            + "! Weltreise abgeschlossen in " + round + " Runden!");
                    break gameLoop;
                }
            }

            round++;
        }

        scanner.close();
    }

    // ── Hilfsmethoden ──────────────────────────────────────────────────────

    private static int rollDice() {
        return DICE.nextInt(6) + 1;
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

    private static void printGameStart(String playerName, GameMode mode,
                                       CityNode startCity, List<CityNode> targets) {
        System.out.println("\n=== WELTREISE: TERMINAL EDITION ===");
        System.out.println("Spieler : " + playerName);
        System.out.println("Modus   : " + mode.name() + " (" + mode.getRequiredTargets() + " Ziele)");
        System.out.println("Startort: " + startCity.getName());
        System.out.println("Zielstädte:");
        targets.forEach(c -> System.out.println("  - " + c.getName() + " (" + c.getContinent() + ")"));
        System.out.println("\nTipp: Nummer eingeben um zu reisen. '0' beendet die Runde. 'quit' beendet das Spiel.\n");
    }
}
