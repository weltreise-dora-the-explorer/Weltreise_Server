package at.aau.serg.websocketdemoserver.messaging.dtos;

public enum GameMode {
    CITY_HOPPER("City Hopper", 6),
    GRAND_TOUR("Grand Tour", 9),
    EPIC_VOYAGE("Epic Voyage", 12);

    private final String displayName;
    private final int stops;

    GameMode(String displayName, int stops) {
        this.displayName = displayName;
        this.stops = stops;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getStops() {
        return stops;
    }
}
