package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistiert Lobby-States atomar in einer JSON-Datei und laedt sie beim Start zurueck.
 * Thread-safe: synchronized saveAll() + atomarer Datei-Rename (tmp -> final).
 */
@Service
public class LobbyPersistence {

    private static final Logger log = LoggerFactory.getLogger(LobbyPersistence.class);
    private static final String FILE_NAME = "lobbies.json";
    private static final String TMP_FILE_NAME = "lobbies.json.tmp";

    private final Path dataDir;
    private final Path lobbiesFile;
    private final Path lobbiesTmpFile;
    private final ObjectMapper objectMapper;

    public LobbyPersistence(@Value("${app.data.dir:./data}") String dataDir) {
        this.dataDir = Paths.get(dataDir);
        this.lobbiesFile = this.dataDir.resolve(FILE_NAME);
        this.lobbiesTmpFile = this.dataDir.resolve(TMP_FILE_NAME);
        this.objectMapper = JsonMapper.builder().build();
    }

    /**
     * Laedt alle persistierten Lobby-States.
     * Gibt eine leere Map zurueck, wenn die Datei fehlt oder korrupt ist
     * (der Spielbetrieb soll auch in diesem Fall starten koennen).
     */
    public Map<String, GameRoomState> loadAll() {
        if (!Files.exists(lobbiesFile)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(
                    lobbiesFile.toFile(),
                    new TypeReference<Map<String, GameRoomState>>() {}
            );
        } catch (JacksonException e) {
            log.error("Failed to load lobbies from {}: {}", lobbiesFile, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Persistiert den aktuellen Stand atomar:
     * 1. komplette Map als Byte-Array serialisieren (in-memory Snapshot)
     * 2. in lobbies.json.tmp schreiben
     * 3. mit ATOMIC_MOVE in lobbies.json umbenennen
     * synchronized stellt sicher, dass nur ein Thread gleichzeitig schreibt.
     */
    public synchronized void saveAll(Map<String, GameRoomState> lobbies) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(lobbies);
            Files.createDirectories(dataDir);
            Files.write(lobbiesTmpFile, bytes);
            Files.move(
                    lobbiesTmpFile,
                    lobbiesFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException | JacksonException e) {
            log.error("Failed to save lobbies to {}: {}", lobbiesFile, e.getMessage());
        }
    }
}
