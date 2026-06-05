package com.ott.api_user.playback.buffer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlaybackCommandQueue {

    private final BlockingQueue<PlaybackCommand> queue;

    public PlaybackCommandQueue(
            @Value("${playback.buffer.queue-capacity:100000}") int capacity,
            PlaybackMetrics playbackMetrics) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        playbackMetrics.bindQueueSize(queue);
    }

    /**
     * Called by API threads. This is non-blocking.
     *
     * @return true when the command is queued, false when the queue is full
     */
    public boolean offer(long memberId, long contentsId, int positionSec) {
        PlaybackCommand command = new PlaybackCommand(
            memberId, contentsId, positionSec, Instant.now()
        );
        return queue.offer(command);
    }

    /**
     * Returns when either bulkSize is reached or the timeout expires.
     */
    public List<PlaybackCommand> drain(int bulkSize, long timeoutMs) throws InterruptedException {
        List<PlaybackCommand> batch = new ArrayList<>(bulkSize);
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (batch.size() < bulkSize) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;

            PlaybackCommand cmd = queue.poll(remaining, TimeUnit.MILLISECONDS);
            if (cmd == null) break;

            batch.add(cmd);
            queue.drainTo(batch, bulkSize - batch.size());
        }

        return batch;
    }

    public int size() {
        return queue.size();
    }
}
