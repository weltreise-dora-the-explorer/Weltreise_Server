package at.aau.serg.websocketdemoserver.game.minigame;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Erzeugt Flaggen-Runden aus {@code /flag-codes.json} (Code -> Ländername).
 * Jede Runde hat eine zufällige Flagge und vier Optionen: die richtige plus
 * drei zufällige andere Ländernamen.
 */
public class FlagQuestionPool {

    private static final int OPTIONS_PER_QUESTION = 4;

    private final Map<String, String> codeToName = new HashMap<>();
    private final List<String> codes = new ArrayList<>();
    private final Random random;

    public FlagQuestionPool() {
        this(new Random());
    }

    public FlagQuestionPool(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        loadFromJson();
    }

    private void loadFromJson() {
        ObjectMapper mapper = JsonMapper.builder().build();
        try (InputStream in = getClass().getResourceAsStream("/flag-codes.json")) {
            if (in == null) {
                throw new IllegalStateException("flag-codes.json not found in resources");
            }
            Map<String, String> loaded = mapper.readValue(in, new TypeReference<Map<String, String>>() {});
            codeToName.putAll(loaded);
            codes.addAll(loaded.keySet());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load flag-codes.json", e);
        }
    }

    /**
     * Erzeugt {@code rounds} Runden mit unterschiedlichen Flaggen.
     */
    public List<FlagQuestion> generateRounds(int rounds) {
        if (rounds <= 0) {
            throw new IllegalArgumentException("rounds must be positive");
        }
        if (rounds > codes.size()) {
            throw new IllegalArgumentException("not enough flags for " + rounds + " distinct rounds");
        }

        List<String> shuffledCodes = new ArrayList<>(codes);
        Collections.shuffle(shuffledCodes, random);

        List<FlagQuestion> result = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            result.add(buildQuestion(shuffledCodes.get(i)));
        }
        return result;
    }

    private FlagQuestion buildQuestion(String code) {
        String correctName = codeToName.get(code);

        List<String> distractors = codes.stream()
                .filter(c -> !c.equals(code))
                .map(codeToName::get)
                .filter(name -> !name.equals(correctName))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(distractors, random);

        List<String> options = new ArrayList<>();
        options.add(correctName);
        for (int i = 0; i < OPTIONS_PER_QUESTION - 1 && i < distractors.size(); i++) {
            options.add(distractors.get(i));
        }
        Collections.shuffle(options, random);

        return new FlagQuestion(code, options, correctName);
    }
}
