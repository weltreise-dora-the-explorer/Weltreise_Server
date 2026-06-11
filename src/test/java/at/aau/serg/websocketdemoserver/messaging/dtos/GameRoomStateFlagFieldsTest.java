package at.aau.serg.websocketdemoserver.messaging.dtos;

import at.aau.serg.websocketdemoserver.game.minigame.FlagQuestion;
import at.aau.serg.websocketdemoserver.game.minigame.MinigameSubPhase;
import at.aau.serg.websocketdemoserver.game.minigame.MinigameType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameRoomStateFlagFieldsTest {

    @Test
    void newFlagFieldsHaveSafeDefaults() {
        GameRoomState state = new GameRoomState();

        assertThat(state.getFlagRoundIndex()).isZero();
        assertThat(state.getFlagOptions()).isEmpty();
        assertThat(state.getFlagScores()).isEmpty();
        assertThat(state.getFlagTotalTimeMs()).isEmpty();
        assertThat(state.getFlagRounds()).isEmpty();
        assertThat(state.getFlagCode()).isNull();
        assertThat(state.getFlagCorrectName()).isNull();
    }

    @Test
    void enumsExposeFlagGameAndRoundReveal() {
        assertThat(MinigameType.values()).contains(MinigameType.FLAG_GAME);
        assertThat(MinigameSubPhase.values()).contains(MinigameSubPhase.ROUND_REVEAL);
    }

    @Test
    void flagRoundsAnswerKeyIsNotSerialized() throws Exception {
        GameRoomState state = new GameRoomState();
        state.setFlagCode("ar");
        state.setFlagOptions(List.of("Argentina", "Germany", "Japan", "Brazil"));
        state.setFlagRounds(List.of(
                new FlagQuestion("ar", List.of("Argentina", "Germany", "Japan", "Brazil"), "Argentina")));

        String json = JsonMapper.builder().build().writeValueAsString(state);

        // Der Antwort-Schlüssel darf NICHT im Broadcast landen (kein Spicken).
        assertThat(json).doesNotContain("flagRounds");
        // Die Anzeige-Felder dagegen schon.
        assertThat(json).contains("flagCode").contains("flagOptions");
    }
}
