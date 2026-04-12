package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GamePhase;
import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import at.aau.serg.websocketdemoserver.game.models.City;
import at.aau.serg.websocketdemoserver.game.models.CityColor;
import at.aau.serg.websocketdemoserver.game.models.Continent;
import at.aau.serg.websocketdemoserver.game.models.PlayerState;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

import java.util.List;
import java.util.Objects;

/**
 * Service für das Verwalten der Spieler-Lobbys (Beitreten, Verlassen, Starten des Spiels).
 */
@Service
public class LobbyService {
    private static final int MIN_PLAYERS_TO_START = 2;

    private final InMemoryLobbyStore lobbyStore;
    private final CityDistributor cityDistributor;

    public LobbyService(InMemoryLobbyStore lobbyStore, CityDistributor cityDistributor) {
        this.lobbyStore = Objects.requireNonNull(lobbyStore, "lobbyStore must not be null");
        this.cityDistributor = Objects.requireNonNull(cityDistributor, "cityDistributor must not be null");
    }

    public GameRoomState joinLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.getOrCreate(lobbyId);

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new IllegalArgumentException("Cannot join started game");
        }
        if (containsPlayer(state.getPlayers(), playerId)) {
            throw new IllegalArgumentException("Player already joined lobby");
        }

        state.getPlayers().add(new PlayerState(playerId));
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    public GameRoomState leaveLobby(String lobbyId, String playerId) {
        validatePlayerId(playerId);
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        int leavingIndex = indexOfPlayer(state.getPlayers(), playerId);
        if (leavingIndex < 0) {
            throw new IllegalArgumentException("Player is not in lobby");
        }

        String previousCurrentPlayer = state.getCurrentPlayerId();
        state.getPlayers().remove(leavingIndex);
        state.setVersion(state.getVersion() + 1);

        if (state.getPlayers().isEmpty()) {
            lobbyStore.remove(lobbyId);
            return state;
        }

        if (previousCurrentPlayer != null && previousCurrentPlayer.equals(playerId)) {
            int nextIndex = leavingIndex % state.getPlayers().size();
            state.setCurrentPlayerId(state.getPlayers().get(nextIndex).getPlayerId());
            state.setLastDiceValue(null);
        }

        return state;
    }

    public GameRoomState startGame(String lobbyId) {
        GameRoomState state = lobbyStore.get(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        if (state.getPhase() != GamePhase.LOBBY) {
            throw new IllegalArgumentException("Game already started");
        }
        if (state.getPlayers().size() < MIN_PLAYERS_TO_START) {
            throw new IllegalArgumentException("At least two players are required");
        }

        state.setPhase(GamePhase.IN_TURN);
        
        // 1. Give each player their required cities (2 per continent = 8 total initially)
        List<City> allCities = getDefaultCities();
        cityDistributor.distributeByContinent(allCities, state.getPlayers(), 2);

        // 2. Setup initial positions (use first target city as start city for sprint 1 simplicity)
        for (PlayerState player : state.getPlayers()) {
            if (!player.getOwnedCities().isEmpty()) {
                City start = player.getOwnedCities().get(0);
                player.setStartCity(start);
                player.setCurrentCity(start);
            }
        }

        state.setCurrentPlayerId(state.getPlayers().getFirst().getPlayerId());
        state.setLastDiceValue(null);
        state.setVersion(state.getVersion() + 1);
        return state;
    }

    private List<City> getDefaultCities() {
        return new ArrayList<>(List.of(
            // Europa
            new City("wien", "Wien", Continent.EUROPE, CityColor.RED),
            new City("berlin", "Berlin", Continent.EUROPE, CityColor.RED),
            new City("paris", "Paris", Continent.EUROPE, CityColor.BLUE),
            new City("rom", "Rom", Continent.EUROPE, CityColor.BLUE),
            new City("madrid", "Madrid", Continent.EUROPE, CityColor.GREEN),
            new City("london", "London", Continent.EUROPE, CityColor.GREEN),
            // Asien
            new City("tokio", "Tokio", Continent.ASIA, CityColor.RED),
            new City("peking", "Peking", Continent.ASIA, CityColor.RED),
            new City("bangkok", "Bangkok", Continent.ASIA, CityColor.BLUE),
            new City("seoul", "Seoul", Continent.ASIA, CityColor.BLUE),
            new City("neu-delhi", "Neu-Delhi", Continent.ASIA, CityColor.GREEN),
            new City("singapur", "Singapur", Continent.ASIA, CityColor.GREEN),
            // Nordamerika
            new City("new-york", "New York", Continent.NORTH_AMERICA, CityColor.RED),
            new City("los-angeles", "Los Angeles", Continent.NORTH_AMERICA, CityColor.RED),
            new City("toronto", "Toronto", Continent.NORTH_AMERICA, CityColor.BLUE),
            new City("chicago", "Chicago", Continent.NORTH_AMERICA, CityColor.BLUE),
            new City("mexiko-stadt", "Mexiko-Stadt", Continent.NORTH_AMERICA, CityColor.GREEN),
            new City("miami", "Miami", Continent.NORTH_AMERICA, CityColor.GREEN),
            // Südamerika
            new City("rio", "Rio de Janeiro", Continent.SOUTH_AMERICA, CityColor.RED),
            new City("buenos-aires", "Buenos Aires", Continent.SOUTH_AMERICA, CityColor.RED),
            new City("lima", "Lima", Continent.SOUTH_AMERICA, CityColor.BLUE),
            new City("bogota", "Bogota", Continent.SOUTH_AMERICA, CityColor.BLUE),
            new City("santiago", "Santiago", Continent.SOUTH_AMERICA, CityColor.GREEN),
            new City("quito", "Quito", Continent.SOUTH_AMERICA, CityColor.GREEN)
        ));
    }

    private boolean containsPlayer(List<PlayerState> players, String playerId) {
        return players.stream().anyMatch(player -> playerId.equals(player.getPlayerId()));
    }

    private int indexOfPlayer(List<PlayerState> players, String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (playerId.equals(players.get(i).getPlayerId())) {
                return i;
            }
        }
        return -1;
    }

    private void validatePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player id is required");
        }
    }
}
