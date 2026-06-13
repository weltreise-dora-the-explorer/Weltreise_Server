package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
            JsonNode root = objectMapper.readTree(lobbiesFile);
            Map<String, GameRoomState> lobbies = objectMapper.treeToValue(
                    root,
                    new TypeReference<Map<String, GameRoomState>>() {}
            );
            restoreClientIds(root, lobbies);
            return lobbies;
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
            JsonNode root = objectMapper.valueToTree(lobbies);
            addClientIds(root, lobbies);
            byte[] bytes = objectMapper.writeValueAsBytes(root);
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

    private void addClientIds(JsonNode root, Map<String, GameRoomState> lobbies) {
        for (Map.Entry<String, GameRoomState> entry : lobbies.entrySet()) {
            JsonNode playersNode = root.path(entry.getKey()).path("players");
            if (!playersNode.isArray()) {
                continue;
            }

            for (int index = 0; index < entry.getValue().getPlayers().size() && index < playersNode.size(); index++) {
                String clientId = entry.getValue().getPlayers().get(index).getClientId();
                JsonNode playerNode = playersNode.get(index);
                if (clientId != null && playerNode instanceof ObjectNode playerObject) {
                    playerObject.put("clientId", clientId);
                }
            }
        }
    }

    private void restoreClientIds(JsonNode root, Map<String, GameRoomState> lobbies) {
        for (Map.Entry<String, GameRoomState> entry : lobbies.entrySet()) {
            JsonNode playersNode = root.path(entry.getKey()).path("players");
            if (!playersNode.isArray()) {
                continue;
            }

            for (JsonNode playerNode : playersNode) {
                String playerId = playerNode.path("playerId").asText(null);
                String clientId = playerNode.path("clientId").asText(null);
                if (playerId == null || clientId == null) {
                    continue;
                }
                entry.getValue().getPlayers().stream()
                        .filter(player -> playerId.equals(player.getPlayerId()))
                        .findFirst()
                        .ifPresent(player -> player.setClientId(clientId));
            }
        }
    }
}
