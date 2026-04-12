package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientCommand {
    private CommandType type;
    private String lobbyId;
    private String playerId;
    private Integer moveSteps;
}
