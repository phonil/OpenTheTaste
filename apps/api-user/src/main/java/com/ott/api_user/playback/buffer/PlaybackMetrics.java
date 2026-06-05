package com.ott.api_user.playback.buffer;

import java.util.concurrent.BlockingQueue;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Playback write-behind 버퍼의 관측 메트릭을 한곳에 모은 컴포넌트.
 *
 * <ul>
 *   <li>playback.queue.size (Gauge)        — 현재 버퍼에 쌓인 command 수</li>
 *   <li>playback.flush.duration (Timer)    — 한 배치를 DB에 flush하는 데 걸린 시간</li>
 *   <li>playback.flush.batch.size (Summary)— flush 1회당 command 수</li>
 *   <li>playback.drop{reason} (Counter)    — 유실된 command 수 (queue_full / flush_failure)</li>
 * </ul>
 */
@Component
public class PlaybackMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer flushTimer;
    private final DistributionSummary flushBatchSize;
    private final Counter queueFullDropCounter;
    private final Counter flushFailureDropCounter;

    public PlaybackMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.flushTimer = Timer.builder("playback.flush.duration")
            .description("Time spent flushing a batch of playback commands to DB")
            .register(meterRegistry);
        this.flushBatchSize = DistributionSummary.builder("playback.flush.batch.size")
            .description("Number of playback commands per flush batch")
            .register(meterRegistry);
        this.queueFullDropCounter = Counter.builder("playback.drop")
            .tag("reason", "queue_full")
            .description("Playback commands dropped because the buffer queue was full")
            .register(meterRegistry);
        this.flushFailureDropCounter = Counter.builder("playback.drop")
            .tag("reason", "flush_failure")
            .description("Playback commands dropped due to flush failure")
            .register(meterRegistry);
    }

    /** 큐 인스턴스의 size를 Gauge로 등록한다. 큐 생성 시 1회 호출. */
    public void bindQueueSize(BlockingQueue<?> queue) {
        Gauge.builder("playback.queue.size", queue, BlockingQueue::size)
            .description("Current number of buffered playback commands waiting to be flushed")
            .register(meterRegistry);
    }

    /** flushAction 실행 시간을 flush.duration에 기록한다. 예외는 그대로 전파한다. */
    public void recordFlush(Runnable flushAction) {
        flushTimer.record(flushAction);
    }

    public void recordFlushBatchSize(int size) {
        flushBatchSize.record(size);
    }

    public void incrementQueueFullDrop() {
        queueFullDropCounter.increment();
    }

    public void incrementFlushFailureDrop(int count) {
        flushFailureDropCounter.increment(count);
    }
}
