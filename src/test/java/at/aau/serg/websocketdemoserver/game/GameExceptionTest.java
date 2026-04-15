package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameExceptionTest {

    @Test
    void storesErrorCodeAndMessage() {
        GameException ex = new GameException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOBBY_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Lobby not found");
    }

    @Test
    void isRuntimeException() {
        GameException ex = new GameException(ErrorCode.INVALID_COMMAND, "bad command");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void allErrorCodesAreAccessible() {
        for (ErrorCode code : ErrorCode.values()) {
            GameException ex = new GameException(code, "test");
            assertThat(ex.getErrorCode()).isEqualTo(code);
        }
    }
}
