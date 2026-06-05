package com.ott.api_user.playback.buffer;

import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaybackFlushWorker {

    private static final long SHUTDOWN_JOIN_TIMEOUT_MS = 3000L;
    private static final long SHUTDOWN_DRAIN_TIMEOUT_MS = 100L;

    private final PlaybackCommandQueue commandQueue;
    private final PlaybackBatchRepository batchRepository;
    private final PlaybackMetrics playbackMetrics;

    @Value("${playback.buffer.bulk-size:1000}")
    private int bulkSize;

    @Value("${playback.buffer.flush-timeout-ms:5000}")
    private long flushTimeoutMs;

    private volatile boolean running = true;
    private Thread leaderThread;

    @PostConstruct
    void init() {
        leaderThread = new Thread(this::leaderLoop, "playback-flush-leader");
        leaderThread.setDaemon(true);
        leaderThread.start();
    }

    private void leaderLoop() {
        while (running) {
            try {
                flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Playback flush loop failed", e);
            }
        }
    }

    private void flush() throws InterruptedException {
        List<PlaybackCommand> drained = commandQueue.drain(bulkSize, flushTimeoutMs);
        if (drained.isEmpty()) return;

        flushBatch(drained, "flush");
    }

    private boolean flushBatch(List<PlaybackCommand> commands, String operation) {
        try {
            playbackMetrics.recordFlush(() -> batchRepository.batchUpdate(commands));
            playbackMetrics.recordFlushBatchSize(commands.size());
            return true;
        } catch (Exception e) {
            playbackMetrics.incrementFlushFailureDrop(commands.size());
            log.warn("Playback {} failed, dropping commands: size={}", operation, commands.size(), e);
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        log.info("Playback flush worker shutting down. queueSize={}", commandQueue.size());
        running = false;
        waitForLeaderToStop();
        drainRemainingQueue();
    }

    private void waitForLeaderToStop() {
        Thread thread = leaderThread;
        if (thread == null) return;

        thread.interrupt();
        try {
            thread.join(SHUTDOWN_JOIN_TIMEOUT_MS);
            if (thread.isAlive()) {
                log.warn("Playback flush leader did not stop within {}ms", SHUTDOWN_JOIN_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for playback flush leader to stop");
        }
    }

    private void drainRemainingQueue() {
        int round = 1;
        while (true) {
            List<PlaybackCommand> remaining;
            try {
                remaining = commandQueue.drain(bulkSize, SHUTDOWN_DRAIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while draining playback queue during shutdown. remainingQueueSize={}",
                    commandQueue.size());
                return;
            }

            if (remaining.isEmpty()) break;

            boolean success = flushBatch(remaining, "shutdown flush #" + round);
            if (!success) break;
            round++;
        }

        int remainingQueueSize = commandQueue.size();
        if (remainingQueueSize > 0) {
            log.warn("Playback flush worker stopped with remaining queued commands: size={}", remainingQueueSize);
            return;
        }
        log.info("Playback flush worker stopped with empty queue");
    }
}
