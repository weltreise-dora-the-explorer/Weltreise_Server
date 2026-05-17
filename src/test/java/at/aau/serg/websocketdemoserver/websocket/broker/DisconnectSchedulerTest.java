package at.aau.serg.websocketdemoserver.websocket.broker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DisconnectSchedulerTest {

    private ScheduledExecutorService executor;
    private DisconnectScheduler scheduler;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
        scheduler = new DisconnectScheduler(executor);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void scheduleRunsCallbackAfterDelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        scheduler.schedule("lobby-1", "player-1", latch::countDown, 1L);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancelPreventsCallbackExecution() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();

        scheduler.schedule("lobby-1", "player-1", counter::incrementAndGet, 1L);
        boolean cancelled = scheduler.cancel("lobby-1", "player-1");

        Thread.sleep(1500);

        assertThat(cancelled).isTrue();
        assertThat(counter.get()).isZero();
    }

    @Test
    void cancelReturnsFalseWhenNoTimerScheduled() {
        boolean cancelled = scheduler.cancel("lobby-1", "player-1");

        assertThat(cancelled).isFalse();
    }

    @Test
    void rescheduleReplacesPreviousTimer() throws InterruptedException {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        scheduler.schedule("lobby-1", "player-1", first::incrementAndGet, 5L);
        scheduler.schedule("lobby-1", "player-1", second::incrementAndGet, 1L);

        Thread.sleep(2000);

        assertThat(first.get()).isZero();
        assertThat(second.get()).isEqualTo(1);
    }

    @Test
    void isScheduledReturnsTrueWhileTimerActive() {
        scheduler.schedule("lobby-1", "player-1", () -> { }, 5L);

        assertThat(scheduler.isScheduled("lobby-1", "player-1")).isTrue();
    }

    @Test
    void isScheduledReturnsFalseAfterCancel() {
        scheduler.schedule("lobby-1", "player-1", () -> { }, 5L);
        scheduler.cancel("lobby-1", "player-1");

        assertThat(scheduler.isScheduled("lobby-1", "player-1")).isFalse();
    }

    @Test
    void isScheduledReturnsFalseAfterTimerFires() throws InterruptedException {
        scheduler.schedule("lobby-1", "player-1", () -> { }, 1L);

        Thread.sleep(1500);

        assertThat(scheduler.isScheduled("lobby-1", "player-1")).isFalse();
    }

    @Test
    void differentPlayersHaveSeparateTimers() throws InterruptedException {
        AtomicInteger playerA = new AtomicInteger();
        AtomicInteger playerB = new AtomicInteger();

        scheduler.schedule("lobby-1", "player-A", playerA::incrementAndGet, 1L);
        scheduler.schedule("lobby-1", "player-B", playerB::incrementAndGet, 1L);

        Thread.sleep(2000);

        assertThat(playerA.get()).isEqualTo(1);
        assertThat(playerB.get()).isEqualTo(1);
    }

    @Test
    void samePlayerInDifferentLobbiesHasSeparateTimers() {
        scheduler.schedule("lobby-1", "player-1", () -> { }, 5L);
        scheduler.schedule("lobby-2", "player-1", () -> { }, 5L);

        assertThat(scheduler.isScheduled("lobby-1", "player-1")).isTrue();
        assertThat(scheduler.isScheduled("lobby-2", "player-1")).isTrue();
    }

    @Test
    void defaultSchedulerHasCorrectGracePeriod() {
        assertThat(DisconnectScheduler.GRACE_PERIOD_SECONDS).isEqualTo(60L);
    }

    @Test
    void shutdownCancelsAllRunningTimers() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        scheduler.schedule("lobby-1", "player-1", counter::incrementAndGet, 5L);
        scheduler.schedule("lobby-2", "player-2", counter::incrementAndGet, 5L);

        scheduler.shutdown();
        Thread.sleep(500);

        assertThat(counter.get()).isZero();
    }
}
