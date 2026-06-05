package com.ott.api_user.playback.buffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PlaybackCommandQueueTest {

    private PlaybackCommandQueue newQueue(int capacity) {
        return new PlaybackCommandQueue(capacity, new PlaybackMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void offer_returnsTrueWhenCapacityAvailable() {
        PlaybackCommandQueue queue = newQueue(10);
        assertThat(queue.offer(1L, 1L, 100)).isTrue();
    }

    @Test
    void offer_returnsFalseWhenQueueFull() {
        PlaybackCommandQueue queue = newQueue(1);
        queue.offer(1L, 1L, 100);

        assertThat(queue.offer(2L, 2L, 200)).isFalse();
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void drain_returnsEmptyWhenQueueEmptyAfterTimeout() throws InterruptedException {
        PlaybackCommandQueue queue = newQueue(10);
        List<PlaybackCommand> result = queue.drain(10, 50);
        assertThat(result).isEmpty();
    }

    @Test
    void drain_returnsBatchUpToBulkSize() throws InterruptedException {
        PlaybackCommandQueue queue = newQueue(100);
        for (int i = 0; i < 5; i++) {
            queue.offer(1L, (long) i, i * 10);
        }
        List<PlaybackCommand> result = queue.drain(3, 100);
        assertThat(result).hasSize(3);
    }

}
