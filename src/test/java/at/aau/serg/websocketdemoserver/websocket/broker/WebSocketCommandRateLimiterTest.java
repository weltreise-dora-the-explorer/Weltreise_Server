package at.aau.serg.websocketdemoserver.websocket.broker;

import at.aau.serg.websocketdemoserver.game.GameException;
import at.aau.serg.websocketdemoserver.messaging.dtos.CommandType;
import at.aau.serg.websocketdemoserver.messaging.dtos.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketCommandRateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final WebSocketCommandRateLimiter limiter = new WebSocketCommandRateLimiter(now::get);

    @Test
    void allowsCommandsWithinGeneralLimit() {
        for (int i = 0; i < WebSocketCommandRateLimiter.GENERAL_LIMIT; i++) {
            assertThatCode(() -> limiter.check("session-1", CommandType.ROLL_DICE))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsCommandsAboveGeneralLimit() {
        for (int i = 0; i < WebSocketCommandRateLimiter.GENERAL_LIMIT; i++) {
            limiter.check("session-1", CommandType.ROLL_DICE);
        }

        assertThatThrownBy(() -> limiter.check("session-1", CommandType.ROLL_DICE))
                .isInstanceOfSatisfying(GameException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @Test
    void generalLimitResetsAfterWindow() {
        for (int i = 0; i < WebSocketCommandRateLimiter.GENERAL_LIMIT; i++) {
            limiter.check("session-1", CommandType.ROLL_DICE);
        }

        now.addAndGet(WebSocketCommandRateLimiter.GENERAL_WINDOW_NANOS);

        assertThatCode(() -> limiter.check("session-1", CommandType.ROLL_DICE))
                .doesNotThrowAnyException();
    }

    @Test
    void appliesStricterLimitToLobbyAccessCommands() {
        for (int i = 0; i < WebSocketCommandRateLimiter.LOBBY_ACCESS_LIMIT; i++) {
            limiter.check("session-1", CommandType.JOIN_LOBBY);
        }

        assertThatThrownBy(() -> limiter.check("session-1", CommandType.REJOIN_LOBBY))
                .isInstanceOfSatisfying(GameException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @Test
    void lobbyAccessLimitResetsAfterWindow() {
        for (int i = 0; i < WebSocketCommandRateLimiter.LOBBY_ACCESS_LIMIT; i++) {
            limiter.check("session-1", CommandType.CREATE_LOBBY);
        }

        now.addAndGet(WebSocketCommandRateLimiter.LOBBY_ACCESS_WINDOW_NANOS);

        assertThatCode(() -> limiter.check("session-1", CommandType.JOIN_LOBBY))
                .doesNotThrowAnyException();
    }

    @Test
    void keepsLimitsSeparatePerSession() {
        for (int i = 0; i < WebSocketCommandRateLimiter.LOBBY_ACCESS_LIMIT; i++) {
            limiter.check("session-1", CommandType.JOIN_LOBBY);
        }

        assertThatCode(() -> limiter.check("session-2", CommandType.JOIN_LOBBY))
                .doesNotThrowAnyException();
    }

    @Test
    void ignoresMissingSessionId() {
        assertThatCode(() -> {
            for (int i = 0; i < WebSocketCommandRateLimiter.GENERAL_LIMIT + 1; i++) {
                limiter.check(null, CommandType.ROLL_DICE);
            }
        }).doesNotThrowAnyException();
    }
}
