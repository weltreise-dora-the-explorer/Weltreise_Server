package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.stereotype.Service;

/**
 * Verwaltet den Spielablauf: Städte besuchen und Siegbedingung prüfen.
 * Portiert von Lukas Bugelnigs GameSession.kt (App) auf den Server.
 */
@Service
public class GameSessionService {

    /**
     * Verarbeitet das Betreten einer Stadt.
     * Aktualisiert die Spielerposition und hakt Pflichtziele in der Liste ab.
     *
     * @param player Der aktuelle Spieler.
     * @param city   Die besuchte Stadt.
     * @return Status-Nachricht über den Besuch.
     */
    public String visitCity(PlayerState player, City city) {
        player.setCurrentCity(city);

        // Prüfen ob die Stadt ein gefordertes Ziel ist
        boolean isTarget = player.getOwnedCities().stream()
                .anyMatch(c -> c.getId().equals(city.getId()));

        // Prüfen ob bereits besucht
        boolean alreadyVisited = player.getVisitedCities().stream()
                .anyMatch(c -> c.getId().equals(city.getId()));

        if (isTarget && !alreadyVisited) {
            player.getVisitedCities().add(city);
            return "Ziel erreicht: " + city.getName() + "! Stand: " + player.getProgressStatus();
        } else if (isTarget) {
            return "Bereits abgehakt: " + city.getName();
        } else {
            return "Zwischenstopp: " + city.getName();
        }
    }

    /**
     * Überprüft die Siegbedingungen:
     * 1. Alle benötigten Pflichtziele müssen besucht sein.
     * 2. Die aktuelle Position muss der startCity entsprechen.
     *
     * @param player Der zu prüfende Spieler.
     * @return true wenn der Spieler gewonnen hat.
     */
    public boolean isVictory(PlayerState player) {
        if (player.getStartCity() == null || player.getCurrentCity() == null) {
            return false;
        }

        boolean allTargetsDone = player.isAllTargetsReached();
        boolean backAtHome = player.getCurrentCity().getId().equals(player.getStartCity().getId());

        return allTargetsDone && backAtHome;
    }
}
