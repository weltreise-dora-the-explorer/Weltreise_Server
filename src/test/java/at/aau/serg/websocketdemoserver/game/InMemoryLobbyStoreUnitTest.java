package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InMemoryLobbyStoreUnitTest {

    private InMemoryLobbyStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryLobbyStore();
    }

    // ========== PUT TESTS ==========

    @Test
    void putStoresLobbyState() {
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        store.put("lobby-1", state);

        assertThat(store.get("lobby-1")).isPresent();
        assertThat(store.get("lobby-1").get().getLobbyId()).isEqualTo("lobby-1");
    }

    @Test
    void putOverwritesExistingLobby() {
        GameRoomState state1 = new GameRoomState();
        state1.setLobbyId("lobby-1");
        state1.setVersion(1L);

        GameRoomState state2 = new GameRoomState();
        state2.setLobbyId("lobby-1");
        state2.setVersion(2L);

        store.put("lobby-1", state1);
        store.put("lobby-1", state2);

        assertThat(store.get("lobby-1").get().getVersion()).isEqualTo(2L);
    }

    @Test
    void putRejectsNullLobbyId() {
        GameRoomState state = new GameRoomState();

        assertThatThrownBy(() -> store.put(null, state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lobby id is required");
    }

    @Test
    void putRejectsBlankLobbyId() {
        GameRoomState state = new GameRoomState();

        assertThatThrownBy(() -> store.put("   ", state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lobby id is required");
    }

    // ========== GET TESTS ==========

    @Test
    void getReturnsEmptyForNonExistentLobby() {
        assertThat(store.get("non-existent")).isEmpty();
    }

    @Test
    void getReturnsLobbyWhenExists() {
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        store.put("lobby-1", state);

        assertThat(store.get("lobby-1")).isPresent();
    }

    @Test
    void getRejectsNullLobbyId() {
        assertThatThrownBy(() -> store.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lobby id is required");
    }

    // ========== GET OR CREATE TESTS ==========

    @Test
    void getOrCreateCreatesNewLobbyWhenNotExists() {
        GameRoomState state = store.getOrCreate("new-lobby");

        assertThat(state).isNotNull();
        assertThat(state.getLobbyId()).isEqualTo("new-lobby");
    }

    @Test
    void getOrCreateReturnsExistingLobby() {
        GameRoomState existing = new GameRoomState();
        existing.setLobbyId("lobby-1");
        existing.setVersion(5L);
        store.put("lobby-1", existing);

        GameRoomState retrieved = store.getOrCreate("lobby-1");

        assertThat(retrieved.getVersion()).isEqualTo(5L);
    }

    // ========== REMOVE TESTS ==========

    @Test
    void removeDeletesLobby() {
        GameRoomState state = new GameRoomState();
        store.put("lobby-1", state);

        store.remove("lobby-1");

        assertThat(store.get("lobby-1")).isEmpty();
    }

    @Test
    void removeDoesNothingForNonExistentLobby() {
        store.remove("non-existent");

        assertThat(store.get("non-existent")).isEmpty();
    }

    @Test
    void removeRejectsNullLobbyId() {
        assertThatThrownBy(() -> store.remove(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lobby id is required");
    }

    // ========== PERSISTENCE INTEGRATION TESTS ==========

    @Test
    void postConstructLoadsStateFromPersistence() {
        LobbyPersistence persistence = mock(LobbyPersistence.class);
        GameRoomState seeded = new GameRoomState();
        seeded.setLobbyId("seeded");
        when(persistence.loadAll()).thenReturn(Map.of("seeded", seeded));

        InMemoryLobbyStore storeWithPersistence = new InMemoryLobbyStore(persistence);
        storeWithPersistence.loadFromPersistence();

        assertThat(storeWithPersistence.get("seeded")).isPresent();
        assertThat(storeWithPersistence.get("seeded").get().getLobbyId()).isEqualTo("seeded");
    }

    @Test
    void putTriggersPersistenceSaveAll() {
        LobbyPersistence persistence = mock(LobbyPersistence.class);
        InMemoryLobbyStore storeWithPersistence = new InMemoryLobbyStore(persistence);
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        storeWithPersistence.put("lobby-1", state);

        verify(persistence).saveAll(anyMap());
    }

    @Test
    void removeTriggersPersistenceSaveAll() {
        LobbyPersistence persistence = mock(LobbyPersistence.class);
        InMemoryLobbyStore storeWithPersistence = new InMemoryLobbyStore(persistence);
        storeWithPersistence.put("lobby-1", new GameRoomState());
        clearInvocations(persistence);

        storeWithPersistence.remove("lobby-1");

        verify(persistence).saveAll(anyMap());
    }

    @Test
    void getOrCreateTriggersSaveOnlyOnNewLobby() {
        LobbyPersistence persistence = mock(LobbyPersistence.class);
        InMemoryLobbyStore storeWithPersistence = new InMemoryLobbyStore(persistence);

        storeWithPersistence.getOrCreate("lobby-1");
        verify(persistence, times(1)).saveAll(anyMap());

        storeWithPersistence.getOrCreate("lobby-1");
        verify(persistence, times(1)).saveAll(anyMap());
    }

    @Test
    void noArgConstructorWorksWithoutPersistence() {
        InMemoryLobbyStore plainStore = new InMemoryLobbyStore();
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");

        plainStore.put("lobby-1", state);

        assertThat(plainStore.get("lobby-1")).isPresent();
    }

    @Test
    void saveTriggersPersistenceSaveAll() {
        LobbyPersistence persistence = mock(LobbyPersistence.class);
        InMemoryLobbyStore storeWithPersistence = new InMemoryLobbyStore(persistence);
        storeWithPersistence.put("lobby-1", new GameRoomState());
        clearInvocations(persistence);

        storeWithPersistence.save();

        verify(persistence).saveAll(anyMap());
    }

    @Test
    void saveIsSafeNoOpWithoutPersistence() {
        InMemoryLobbyStore plainStore = new InMemoryLobbyStore();

        plainStore.save();
        // no exception thrown, backward compat preserved
    }
}
