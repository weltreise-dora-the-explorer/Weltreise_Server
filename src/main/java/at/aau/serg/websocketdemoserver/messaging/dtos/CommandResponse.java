package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * DTO für Antworten des Servers auf Client-Kommandos (inklusive Fehlermeldungen etc.).
 */
public class CommandResponse {
    private boolean success;
    private String message;
    private String lobbyId;
    private CommandType commandType;
    private GameRoomState state;
}
