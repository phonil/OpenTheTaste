package com.ott.api_user.playback.cache;

public record PlayableMediaCacheValue(
        Long contentsId,
        boolean playable
) {

    public static PlayableMediaCacheValue valid(Long contentsId) {
        return new PlayableMediaCacheValue(contentsId, true);
    }

    public static PlayableMediaCacheValue invalid() {
        return new PlayableMediaCacheValue(null, false);
    }
}
