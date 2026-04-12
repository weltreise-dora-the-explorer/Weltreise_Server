package at.aau.serg.websocketdemoserver.messaging.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRoomState {
    private String lobbyId;
    private List<PlayerState> players = new ArrayList<>();
    private GamePhase phase = GamePhase.LOBBY;
    private String currentPlayerId;
    private Integer lastDiceValue;
    private long version = 0L;
}
