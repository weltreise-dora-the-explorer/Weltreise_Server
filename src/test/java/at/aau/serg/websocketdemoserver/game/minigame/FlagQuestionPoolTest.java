package at.aau.serg.websocketdemoserver.game.minigame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagQuestionPoolTest {

    @Test
    void generateRoundsProducesFiveDistinctValidQuestions() {
        FlagQuestionPool pool = new FlagQuestionPool(new Random(1));

        List<FlagQuestion> rounds = pool.generateRounds(5);

        assertThat(rounds).hasSize(5);
        assertThat(rounds.stream().map(FlagQuestion::getFlagCode).distinct()).hasSize(5);

        for (FlagQuestion question : rounds) {
            assertThat(question.getOptions()).hasSize(4);
            assertThat(question.getOptions()).doesNotHaveDuplicates();
            assertThat(question.getOptions()).contains(question.getCorrectName());
            assertThat(question.getFlagCode()).isNotBlank();
            assertThat(question.getCorrectName()).isNotBlank();
        }
    }

    @Test
    void generateRoundsRejectsNonPositiveCount() {
        FlagQuestionPool pool = new FlagQuestionPool(new Random(1));

        assertThatThrownBy(() -> pool.generateRounds(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
