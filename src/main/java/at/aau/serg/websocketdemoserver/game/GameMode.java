package at.aau.serg.websocketdemoserver.game;

/**
 * Spielmodi mit der jeweils benötigten Anzahl an Zielstädten für einen Sieg.
 * Portiert von Lukas Bugelnigs GameMode.kt (App).
 */
public enum GameMode {
    TEST_MODE(1),
    CITY_HOPPER(4),
    QUICK_PLAY(6),
    GRAND_TOUR(7),
    STANDARD(9);

    private final int requiredTargets;

    GameMode(int requiredTargets) {
        this.requiredTargets = requiredTargets;
    }

    public int getRequiredTargets() {
        return requiredTargets;
    }
}
