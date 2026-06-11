package at.aau.serg.websocketdemoserver.game.minigame;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagQuestionPoolTest {

    private static Map<String, String> loadCodeToName() {
        try (InputStream in = FlagQuestionPoolTest.class.getResourceAsStream("/flag-codes.json")) {
            return JsonMapper.builder().build()
                    .readValue(in, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

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

        assertThatThrownBy(() -> pool.generateRounds(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pool.generateRounds(-3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateRoundsRejectsMoreRoundsThanAvailableFlags() {
        FlagQuestionPool pool = new FlagQuestionPool(new Random(1));

        assertThatThrownBy(() -> pool.generateRounds(100_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateRoundsIsDeterministicForSameSeed() {
        List<String> first = new FlagQuestionPool(new Random(42)).generateRounds(5)
                .stream().map(FlagQuestion::getFlagCode).toList();
        List<String> second = new FlagQuestionPool(new Random(42)).generateRounds(5)
                .stream().map(FlagQuestion::getFlagCode).toList();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void everyOptionIsAKnownCountryAndCorrectMatchesTheFlagCode() {
        Map<String, String> codeToName = loadCodeToName();
        FlagQuestionPool pool = new FlagQuestionPool(new Random(7));

        for (FlagQuestion question : pool.generateRounds(50)) {
            // correctName ist exakt der Name zum flagCode
            assertThat(question.getCorrectName()).isEqualTo(codeToName.get(question.getFlagCode()));
            // alle Optionen sind echte Ländernamen aus der Resource
            assertThat(codeToName.values()).containsAll(question.getOptions());
            // die richtige Antwort kommt genau einmal vor
            long correctCount = question.getOptions().stream()
                    .filter(o -> o.equals(question.getCorrectName())).count();
            assertThat(correctCount).isEqualTo(1L);
        }
    }
}
