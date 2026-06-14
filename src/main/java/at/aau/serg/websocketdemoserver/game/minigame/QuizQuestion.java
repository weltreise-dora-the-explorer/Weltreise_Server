package at.aau.serg.websocketdemoserver.game.minigame;

import lombok.Getter;
import java.util.List;

//Eine Quiz-Runde: die Frage, vier Antwortoptionen (gemischt) und die korrekte Antwort

@Getter
public class QuizQuestion {
    private final String id;
    private final String questionText;
    private final List<String> options;
    private final String correctAnswer;

    public QuizQuestion(String id, String questionText, List<String> options, String correctAnswer) {
        this.id = id;
        this.questionText = questionText;
        this.options = options;
        this.correctAnswer = correctAnswer;
    }
}