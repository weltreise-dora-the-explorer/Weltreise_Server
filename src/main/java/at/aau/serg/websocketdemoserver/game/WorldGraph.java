package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.CityNode;
import at.aau.serg.websocketdemoserver.game.models.ConnectionType;
import at.aau.serg.websocketdemoserver.game.models.Continent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baut den Städtegraphen mit Zug- und Flugverbindungen aus der cities.json.
 * Verwendet keinen externen JSON-Parser (gleicher Ansatz wie bestehender GameCLI-Parser).
 * Portiert von Lukas Bugelnigs WorldGraph.kt (App).
 */
public class WorldGraph {

    private final Map<String, CityNode> cities = new LinkedHashMap<>();

    public WorldGraph(String json) {
        parse(json);
    }

    public static WorldGraph loadFromResources() throws Exception {
        InputStream in = WorldGraph.class.getClassLoader().getResourceAsStream("cities.json");
        if (in == null) throw new IllegalStateException("cities.json nicht in resources gefunden");
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new WorldGraph(json);
    }

    public CityNode getCityById(String id) {
        return cities.get(id);
    }

    public Collection<CityNode> getAllCities() {
        return cities.values();
    }

    // -------------------------------------------------------------------------
    // JSON-Parsing (ohne Bibliothek)
    // -------------------------------------------------------------------------

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]+)\\}", Pattern.DOTALL);
    private static final Pattern STRING_FIELD   = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ARRAY_FIELD    = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\[([^\\]]*)]");
    private static final Pattern ARRAY_ITEM     = Pattern.compile("\"([^\"]+)\"");

    /** Durchlauf 1+2: Knoten erstellen, dann Kanten knüpfen. */
    private void parse(String json) {
        // Rohdaten pro Stadt sammeln
        List<Map<String, Object>> rawList = new ArrayList<>();

        Matcher obj = OBJECT_PATTERN.matcher(json);
        while (obj.find()) {
            String block = obj.group(1);
            Map<String, Object> entry = new LinkedHashMap<>();

            // Einfache String-Felder
            Matcher sf = STRING_FIELD.matcher(block);
            while (sf.find()) entry.put(sf.group(1), sf.group(2));

            // Array-Felder (trainConnections, flightConnections)
            Matcher af = ARRAY_FIELD.matcher(block);
            while (af.find()) {
                List<String> items = new ArrayList<>();
                Matcher ai = ARRAY_ITEM.matcher(af.group(2));
                while (ai.find()) items.add(ai.group(1));
                entry.put(af.group(1), items);
            }

            if (entry.containsKey("id") && entry.containsKey("name")
                    && entry.containsKey("continent") && entry.containsKey("color")) {
                rawList.add(entry);
            }
        }

        // Durchlauf 1: Knoten anlegen
        for (Map<String, Object> e : rawList) {
            try {
                CityNode node = new CityNode(
                        (String) e.get("id"),
                        (String) e.get("name"),
                        Continent.valueOf((String) e.get("continent")),
                        CityColor.valueOf(((String) e.get("color")).toUpperCase())
                );
                cities.put(node.getId(), node);
            } catch (IllegalArgumentException ignored) {
                // Unbekannter Kontinent oder Farbe — Stadt überspringen
            }
        }

        // Durchlauf 2: Kanten knüpfen
        for (Map<String, Object> e : rawList) {
            CityNode source = cities.get(e.get("id"));
            if (source == null) continue;

            addConnections(source, e, "trainConnections",  ConnectionType.TRAIN);
            addConnections(source, e, "flightConnections", ConnectionType.FLIGHT);
        }
    }

    @SuppressWarnings("unchecked")
    private void addConnections(CityNode source, Map<String, Object> entry,
                                String key, ConnectionType type) {
        Object raw = entry.get(key);
        if (!(raw instanceof List)) return;
        for (String targetId : (List<String>) raw) {
            CityNode target = cities.get(targetId);
            if (target != null) source.addConnection(target, type);
        }
    }
}
