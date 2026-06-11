package at.aau.serg.websocketdemoserver.game.minigame;

import lombok.Getter;

import java.util.List;

/**
 * Eine Flaggen-Runde: die anzuzeigende Flagge (Ländercode), vier
 * Antwortoptionen (Ländernamen, gemischt) und der korrekte Name.
 */
@Getter
public class FlagQuestion {
    private final String flagCode;
    private final List<String> options;
    private final String correctName;

    public FlagQuestion(String flagCode, List<String> options, String correctName) {
        this.flagCode = flagCode;
        this.options = options;
        this.correctName = correctName;
    }
}
