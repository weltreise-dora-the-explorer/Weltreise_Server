package at.aau.serg.websocketdemoserver.game.minigame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;

import java.io.InputStream;
import java.util.*;

/**
 * Erzeugt Quiz-Runden aus {@code /quiz-questions.json}.
 * Jede Runde hat eine zufällige Frage und vier Optionen (korrekt + drei falsche, gemischt).
 */

public class QuizQuestionPool {

    private static final int OPTIONS_PER_QUESTION = 4;
    private final List<QuizQuestionTemplate> allQuestions = new ArrayList<>();
    private final Random random;

    public QuizQuestionPool() {
        this(new Random());
    }

    public QuizQuestionPool(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        loadFromJson();
    }

    private void loadFromJson() {
        ObjectMapper mapper = JsonMapper.builder().build();
        try (InputStream in = getClass().getResourceAsStream("/quiz-questions.json")) {
            if (in == null) {
                throw new IllegalStateException("quiz-questions.json not found in resources");
            }
            // Wir lesen eine Liste unserer Templates aus der JSON
            List<QuizQuestionTemplate> loaded = mapper.readValue(in, new TypeReference<List<QuizQuestionTemplate>>() {});
            allQuestions.addAll(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load quiz-questions.json", e);
        }
    }

    //Zieht eine zufällige Frage und mischt die Antworten.
    public QuizQuestion generateRandomQuestion() {
        if (allQuestions.isEmpty()) {
            throw new IllegalStateException("No questions available in pool");
        }

        //Zufällige Frage auswählen
        QuizQuestionTemplate template = allQuestions.get(random.nextInt(allQuestions.size()));

        //Antworten zusammenstellen (richtige + falsche)
        List<String> options = new ArrayList<>();
        options.add(template.getCorrectAnswer());

        //Fügt falsche Antworten hinzu
        List<String> wrongAnswers = new ArrayList<>(template.getWrongAnswers());
        Collections.shuffle(wrongAnswers, random);
        for (int i = 0; i < wrongAnswers.size() && options.size() < OPTIONS_PER_QUESTION; i++) {
            options.add(wrongAnswers.get(i));
        }

        //Mischen von Antwortenanordnung
        Collections.shuffle(options, random);

        return new QuizQuestion(
                template.getId(),
                template.getQuestionText(),
                options,
                template.getCorrectAnswer()
        );
    }

    //Einlesen der JSON-Daten
    @Getter
    private static class QuizQuestionTemplate {
        private String id;
        private String questionText;
        private String correctAnswer;
        private List<String> wrongAnswers;
    }
}