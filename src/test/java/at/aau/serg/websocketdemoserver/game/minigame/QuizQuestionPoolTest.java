package at.aau.serg.websocketdemoserver.game.minigame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuizQuestionPoolTest {

    @Test
    void testPoolLoadsSuccessfullyAndGeneratesQuestion() {
        // Arrange & Act
        QuizQuestionPool pool = new QuizQuestionPool();
        QuizQuestion question = pool.generateRandomQuestion();

        // Assert
        assertNotNull(question, "Die generierte Frage darf nicht null sein.");
        assertNotNull(question.getId(), "Die Frage muss eine ID haben.");
        assertNotNull(question.getQuestionText(), "Der Fragetext darf nicht null sein.");
        assertNotNull(question.getCorrectAnswer(), "Es muss eine richtige Antwort geben.");
    }

    @Test
    void testQuestionHasExactlyFourOptions() {
        // Arrange
        QuizQuestionPool pool = new QuizQuestionPool();

        // Act
        QuizQuestion question = pool.generateRandomQuestion();

        // Assert
        assertEquals(4, question.getOptions().size(), "Es müssen exakt 4 Antwortmöglichkeiten generiert werden.");
        assertTrue(question.getOptions().contains(question.getCorrectAnswer()), "Die richtige Antwort muss zwingend in den gemischten Optionen enthalten sein.");
    }
}