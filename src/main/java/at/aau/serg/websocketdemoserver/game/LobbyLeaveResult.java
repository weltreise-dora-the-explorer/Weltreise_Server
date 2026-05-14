package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;

public record LobbyLeaveResult(GameRoomState state, boolean lobbyClosed) {}
