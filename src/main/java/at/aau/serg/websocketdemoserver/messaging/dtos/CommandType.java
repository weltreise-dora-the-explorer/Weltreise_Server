package at.aau.serg.websocketdemoserver.messaging.dtos;

/**
 * Enum für die Typen an Kommandos, die ein Client an den Server schicken kann.
 */
public enum CommandType {
    CREATE_LOBBY,
    JOIN_LOBBY,
    START_GAME,
    ROLL_DICE,
    MOVE_TOKEN,
    LEAVE_LOBBY
}
