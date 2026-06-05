package com.ott.api_user.playlist.service;

import com.ott.api_user.playlist.dto.response.PlaylistResponse;
import com.ott.common.web.response.SliceInfo;
import com.ott.common.web.response.SliceResponse;
import com.ott.domain.common.MediaType;
import com.ott.domain.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrendingCacheService {

    private final MediaRepository mediaRepository;
    private final ContentsRepository contentsRepository;

    @Cacheable(value = "trending", key = "#page + ':' + #size", cacheManager = "redisCacheManager")
    public SliceResponse<PlaylistResponse> getTrending(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        // 1. 인기 TOP 20 조회
        Slice<Media> mediaPage = mediaRepository.findTrendingPlaylists(null, null, pageable);

        // 2. 시리즈의 1화 mediaId 일괄 조회
        List<Long> seriesMediaIdList = mediaPage.getContent().stream()
                .filter(m -> m.getMediaType() == MediaType.SERIES)
                .map(Media::getId)
                .toList();

        Map<Long, Long> firstEpisodeMap = seriesMediaIdList.isEmpty()
                ? new HashMap<>()
                : contentsRepository.findFirstEpisodeMediaIdsBySeriesMediaIds(seriesMediaIdList);

        // 3. 시리즈 → 1화, 단편 → 자기 자신으로 targetMediaId 결정
        Map<Long, Long> mediaToTargetIdMap = new HashMap<>();
        for (Media media : mediaPage.getContent()) {
            if (media.getMediaType() == MediaType.SERIES) {
                mediaToTargetIdMap.put(media.getId(), firstEpisodeMap.get(media.getId()));
            } else {
                mediaToTargetIdMap.put(media.getId(), media.getId());
            }
        }

        // 4. duration 조회 (1화 기준)
        List<Long> targetMediaIdList = mediaToTargetIdMap.values().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Integer> durationMap = targetMediaIdList.isEmpty()
                ? new HashMap<>()
                : contentsRepository.findAllByMediaIdIn(targetMediaIdList).stream()
                    .collect(Collectors.toMap(
                            c -> c.getMedia().getId(),
                            c -> c.getDuration() != null ? c.getDuration() : 0,
                            (existing, replacement) -> existing
                    ));

        // 5. DTO 변환 (positionSec = 0 고정)
        List<PlaylistResponse> contentList = mediaPage.getContent().stream()
                .map(media -> {
                    Long targetId = mediaToTargetIdMap.get(media.getId());
                    Integer duration = targetId != null ? durationMap.getOrDefault(targetId, 0) : 0;
                    return PlaylistResponse.from(media, duration, 0);
                })
                .toList();

        // 6. SliceInfo
        SliceInfo sliceInfo = SliceInfo.builder()
                .currentPage(mediaPage.getNumber())
                .pageSize(mediaPage.getSize())
                .hasNext(mediaPage.hasNext())
                .build();

        return SliceResponse.toSliceResponse(sliceInfo, contentList);
    }
}
