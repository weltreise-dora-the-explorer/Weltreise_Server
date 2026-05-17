package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/*
 * DTO für Befehle vom Client (z. B. Würfeln, Figur bewegen, Lobby beitreten).
 */
public class ClientCommand {
    private CommandType type;
    private String lobbyId;
    private String playerId;
    private Integer moveSteps;
    private Integer stops;
    private String targetCityId;
    private GameMode gameMode;
    private String clientId;

    public ClientCommand(CommandType type, String lobbyId, String playerId, Integer moveSteps, Integer stops) {
        this(type, lobbyId, playerId, moveSteps, stops, null, null, null);
    }

    public ClientCommand(CommandType type, String lobbyId, String playerId, Integer moveSteps, Integer stops,
                         String targetCityId, GameMode gameMode) {
        this(type, lobbyId, playerId, moveSteps, stops, targetCityId, gameMode, null);
    }
}
