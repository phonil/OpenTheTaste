package com.ott.api_user.playback.buffer;

/**
 * Coalescing key. 같은 사용자 + 같은 콘텐츠 = 같은 key.
 */
public record PlaybackKey(
        long memberId,
        long contentsId
) {
}
