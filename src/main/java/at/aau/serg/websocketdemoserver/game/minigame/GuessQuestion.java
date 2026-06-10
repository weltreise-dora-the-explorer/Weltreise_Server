package at.aau.serg.websocketdemoserver.game.minigame;

import lombok.Getter;

@Getter
public class GuessQuestion {
    private final int id;
    private final String questionText;
    private final long correctAnswer;

    public GuessQuestion(int id, String questionText, long correctAnswer) {
        this.id = id;
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;
    }
}
