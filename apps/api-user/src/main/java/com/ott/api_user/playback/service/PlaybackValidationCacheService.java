package com.ott.api_user.playback.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ott.api_user.config.CacheConfig;
import com.ott.api_user.playback.cache.PlayableMediaCacheValue;
import com.ott.domain.contents.repository.ContentsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaybackValidationCacheService {

    private final ContentsRepository contentsRepository;

    @Cacheable(
            cacheNames = CacheConfig.PLAYBACK_PLAYABLE_MEDIA_CACHE,
            key = "#mediaId",
            cacheManager = "caffeineCacheManager")
    public PlayableMediaCacheValue getPlayableMedia(Long mediaId) {
        return contentsRepository.findPlayableContentsIdByMediaId(mediaId)
                .map(PlayableMediaCacheValue::valid)
                .orElseGet(PlayableMediaCacheValue::invalid);
    }

    @CacheEvict(
            cacheNames = CacheConfig.PLAYBACK_PLAYABLE_MEDIA_CACHE,
            key = "#mediaId",
            cacheManager = "caffeineCacheManager")
    public void evict(Long mediaId) {
    }
}
