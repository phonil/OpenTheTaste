package com.ott.api_user.playback.buffer;

import java.time.Instant;

/**
 * Queue에 들어가는 command 단위.
 * requestedAt: API 요청 시점. coalescing/정합성 기준.
 */
public record PlaybackCommand(
    long memberId,
    long contentsId,
    int positionSec,
    Instant requestedAt
) {
    public PlaybackKey key() {
        return new PlaybackKey(memberId, contentsId);
    }
}
