package at.aau.serg.websocketdemoserver.game;

import at.aau.serg.websocketdemoserver.messaging.dtos.GameRoomState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LobbyPersistenceTest {

    @Test
    void saveAllAndLoadAllRoundtripPreservesState(@TempDir Path tempDir) {
        LobbyPersistence persistence = new LobbyPersistence(tempDir.toString());

        Map<String, GameRoomState> input = new HashMap<>();
        GameRoomState state = new GameRoomState();
        state.setLobbyId("lobby-1");
        state.setHostId("host-1");
        state.setVersion(42L);
        input.put("lobby-1", state);

        persistence.saveAll(input);
        Map<String, GameRoomState> loaded = persistence.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get("lobby-1").getLobbyId()).isEqualTo("lobby-1");
        assertThat(loaded.get("lobby-1").getHostId()).isEqualTo("host-1");
        assertThat(loaded.get("lobby-1").getVersion()).isEqualTo(42L);
    }

    @Test
    void loadAllReturnsEmptyMapWhenFileMissing(@TempDir Path tempDir) {
        LobbyPersistence persistence = new LobbyPersistence(tempDir.toString());

        Map<String, GameRoomState> loaded = persistence.loadAll();

        assertThat(loaded).isEmpty();
    }

    @Test
    void loadAllReturnsEmptyMapForCorruptJson(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("lobbies.json"), "{ this is not valid json");
        LobbyPersistence persistence = new LobbyPersistence(tempDir.toString());

        Map<String, GameRoomState> loaded = persistence.loadAll();

        assertThat(loaded).isEmpty();
    }

    @Test
    void saveAllCreatesDataDirectoryIfMissing(@TempDir Path tempDir) {
        Path nestedDir = tempDir.resolve("nonexistent/data");
        LobbyPersistence persistence = new LobbyPersistence(nestedDir.toString());

        Map<String, GameRoomState> input = new HashMap<>();
        GameRoomState state = new GameRoomState();
        state.setLobbyId("x");
        input.put("x", state);

        persistence.saveAll(input);

        assertThat(Files.exists(nestedDir.resolve("lobbies.json"))).isTrue();
    }

    @Test
    void concurrentSaveAllProducesValidJsonFile(@TempDir Path tempDir) throws Exception {
        LobbyPersistence persistence = new LobbyPersistence(tempDir.toString());
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    Map<String, GameRoomState> map = new HashMap<>();
                    GameRoomState state = new GameRoomState();
                    state.setLobbyId("lobby-" + n);
                    state.setVersion(n);
                    map.put(state.getLobbyId(), state);
                    persistence.saveAll(map);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        // Last write wins, but the file MUST contain valid JSON with one entry.
        Map<String, GameRoomState> loaded = persistence.loadAll();
        assertThat(loaded).hasSize(1);
    }

    @Test
    void concurrentReadsDuringWritesAlwaysSeeValidJson(@TempDir Path tempDir) throws Exception {
        LobbyPersistence persistence = new LobbyPersistence(tempDir.toString());

        // Seed with a non-empty state so readers can detect empty (= corrupt) reads.
        Map<String, GameRoomState> seed = new HashMap<>();
        GameRoomState seedState = new GameRoomState();
        seedState.setLobbyId("seed");
        seed.put("seed", seedState);
        persistence.saveAll(seed);

        int writers = 4;
        int readers = 4;
        int iterations = 100;
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers + readers);

        for (int i = 0; i < writers; i++) {
            final int wId = i;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        Map<String, GameRoomState> map = new HashMap<>();
                        GameRoomState state = new GameRoomState();
                        state.setLobbyId("w" + wId + "-" + j);
                        state.setVersion(j);
                        map.put(state.getLobbyId(), state);
                        persistence.saveAll(map);
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < readers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        Map<String, GameRoomState> loaded = persistence.loadAll();
                        // Half-written or corrupt JSON would cause loadAll to swallow
                        // the parse error and return an empty map. Atomic move
                        // guarantees the final path always points to a fully-written
                        // non-empty map, so reads must never be empty here.
                        if (loaded.isEmpty()) {
                            firstError.compareAndSet(null,
                                    new AssertionError("Reader observed empty map mid-stream"));
                        }
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        if (firstError.get() != null) {
            throw new AssertionError("Worker observed unexpected state", firstError.get());
        }
    }
}
