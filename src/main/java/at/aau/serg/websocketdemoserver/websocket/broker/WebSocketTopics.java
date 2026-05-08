package at.aau.serg.websocketdemoserver.websocket.broker;

public final class WebSocketTopics {

    private WebSocketTopics() {}

    public static final String GOAL_REACHED = "/topic/goal-reached";
    public static final String GAME_OVER = "/topic/game-over";

    public static String lobbyEvents(String lobbyId) {
        return "/topic/lobby/" + lobbyId + "/events";
    }
}
