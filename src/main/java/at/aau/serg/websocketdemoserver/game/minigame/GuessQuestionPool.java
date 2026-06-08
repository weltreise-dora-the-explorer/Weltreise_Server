package at.aau.serg.websocketdemoserver.game.minigame;

import java.util.List;
import java.util.Random;

public class GuessQuestionPool {
    private static final List<GuessQuestion> QUESTIONS = List.of(
        new GuessQuestion(1,  "Wie viele Einwohner hat Tokio? (in Millionen, gerundet)", 14),
        new GuessQuestion(2,  "Wie lang ist der Nil in Kilometern?", 6650),
        new GuessQuestion(3,  "Auf welcher Höhe liegt der Mount Everest in Metern?", 8849),
        new GuessQuestion(4,  "Wie groß ist die Fläche Australiens in km²?", 7692024),
        new GuessQuestion(5,  "Wie viele Länder hat Afrika?", 54),
        new GuessQuestion(6,  "Wie lang ist der Amazonas in Kilometern?", 6400),
        new GuessQuestion(7,  "Wie tief ist der Marianengraben in Metern?", 11034),
        new GuessQuestion(8,  "Wie viele Einwohner hat Indien (in Millionen, gerundet)?", 1400),
        new GuessQuestion(9,  "Wie viele km entfernt ist der Mond von der Erde?", 384400),
        new GuessQuestion(10, "Wie viele Länder liegen in Europa?", 44)
    );

    private final Random random = new Random();

    public GuessQuestion getRandom() {
        return QUESTIONS.get(random.nextInt(QUESTIONS.size()));
    }
}
