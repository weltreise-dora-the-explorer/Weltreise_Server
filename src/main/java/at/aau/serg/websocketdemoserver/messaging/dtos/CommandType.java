package at.aau.serg.websocketdemoserver.messaging.dtos;

/**
 * Enum für die Typen an Kommandos, die ein Client an den Server schicken kann.
 */
public enum CommandType {
    CREATE_LOBBY,
    JOIN_LOBBY,
    START_GAME,
    UPDATE_GAME_MODE,
    ROLL_DICE,
    MOVE_TOKEN,
    MOVE_TO_CITY,
    END_TURN,
    START_MINIGAME,
    SUBMIT_GUESS,
    SUBMIT_QUIZ_ANSWER,
    ANNOUNCE_MINIGAME_RESULT,
    FINISH_MINIGAME,
    REACTION_READY,
    REACTION_PRESS,
    USE_FREE_PASS,
    USE_SHAKE_CHEAT,
    REPORT_CHEAT,
    LEAVE_LOBBY,
    LOBBY_CLOSED,
    RESET_LOBBY,
    REJOIN_LOBBY,
    PLAYER_DISCONNECTED,
    PLAYER_RECONNECTED
}
