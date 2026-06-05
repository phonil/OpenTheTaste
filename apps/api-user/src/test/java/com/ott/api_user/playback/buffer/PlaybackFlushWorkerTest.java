package com.ott.api_user.playback.buffer;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PlaybackFlushWorkerTest {

    @Mock
    private PlaybackCommandQueue commandQueue;

    @Mock
    private PlaybackBatchRepository batchRepository;

    @Spy
    private PlaybackMetrics playbackMetrics = new PlaybackMetrics(new SimpleMeterRegistry());

    @InjectMocks
    private PlaybackFlushWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "bulkSize", 2);
        ReflectionTestUtils.setField(worker, "flushTimeoutMs", 50L);
        ReflectionTestUtils.setField(worker, "leaderThread", new Thread(() -> {
        }));
    }

    @Test
    void flush_dropsBatchWhenBatchUpdateFails() throws InterruptedException {
        List<PlaybackCommand> batch = List.of(command(1L, 10L, 100));
        when(commandQueue.drain(2, 50L)).thenReturn(batch);
        doThrow(new RuntimeException("db down")).when(batchRepository).batchUpdate(batch);

        assertThatNoException().isThrownBy(() -> ReflectionTestUtils.invokeMethod(worker, "flush"));

        verify(batchRepository).batchUpdate(batch);
    }

    @Test
    void shutdown_drainsRemainingQueueUntilEmptyAfterLeaderStops() throws InterruptedException {
        List<PlaybackCommand> firstBatch = List.of(
            command(1L, 10L, 100),
            command(2L, 20L, 200)
        );
        List<PlaybackCommand> secondBatch = List.of(command(3L, 30L, 300));

        when(commandQueue.size()).thenReturn(3, 0);
        when(commandQueue.drain(2, 100L)).thenReturn(firstBatch, secondBatch, List.of());

        worker.shutdown();

        verify(commandQueue, times(3)).drain(2, 100L);
        verify(batchRepository).batchUpdate(firstBatch);
        verify(batchRepository).batchUpdate(secondBatch);
    }

    private PlaybackCommand command(long memberId, long contentsId, int positionSec) {
        return new PlaybackCommand(memberId, contentsId, positionSec, Instant.now());
    }
}
