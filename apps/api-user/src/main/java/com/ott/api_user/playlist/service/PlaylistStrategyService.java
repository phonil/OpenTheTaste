package com.ott.api_user.playlist.service;

import com.ott.api_user.common.ContentSource;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.api_user.playlist.dto.response.PlaylistResponse;
import com.ott.api_user.playlist.dto.response.TopTagPlaylistResponse;
import com.ott.api_user.playlist.service.strategy.PlaylistStrategy;
import com.ott.common.web.exception.BusinessException;
import com.ott.common.web.exception.ErrorCode;
import com.ott.common.web.response.SliceInfo;
import com.ott.common.web.response.SliceResponse;
import com.ott.domain.common.MediaType;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.playback.repository.PlaybackRepository;
import com.ott.domain.tag.domain.Tag;
import com.ott.domain.watch_history.repository.WatchHistoryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistStrategyService {

    private final Map<String, PlaylistStrategy> strategyMap;
    private final PlaylistPreferenceService preferenceService;
    private final WatchHistoryRepository watchHistoryRepository;
    private final ContentsRepository contentsRepository;
    private final PlaybackRepository playbackRepository;

    public SliceResponse<PlaylistResponse> getPlaylists(PlaylistCondition condition, Pageable pageable) {

        if (condition.getContentSource() == null) {
             throw new BusinessException(ErrorCode.INVALID_PLAYLIST_SOURCE);
        }

        // 1. 전략 선택 및 1차 데이터 조회
        PlaylistStrategy strategy = getStrategy(condition);
        Slice<Media> mediaPage = strategy.getPlaylist(condition, pageable);
        Long memberId = condition.getMemberId();

        // [Before] N+1 개별 조회 (시리즈 20개 기준 최대 60회 쿼리)
        // Map<Long, Long> mediaToTargetIdMap = new HashMap<>();
        // for (Media media : mediaPage.getContent()) {
        //     if (media.getMediaType() == MediaType.SERIES) {
        //         Long targetId = watchHistoryRepository.findLatestContentMediaIdByMemberIdAndSeriesMediaId(memberId, media.getId())
        //                 .orElseGet(() -> getFirstEpisodeMediaId(media.getId()));
        //         mediaToTargetIdMap.put(media.getId(), targetId);
        //     } else {
        //         mediaToTargetIdMap.put(media.getId(), media.getId());
        //     }
        // }

        // [After] IN절 일괄 조회 (최대 2회 쿼리)
        // 1) 시리즈 ID 목록 추출
        List<Long> seriesMediaIdList = mediaPage.getContent().stream()
                .filter(m -> m.getMediaType() == MediaType.SERIES)
                .map(Media::getId)
                .toList();

        // 2) 시리즈별 최근 시청 에피소드 일괄 조회 (1회)
        Map<Long, Long> latestEpisodeMap = seriesMediaIdList.isEmpty()
                ? new HashMap<>()
                : watchHistoryRepository.findLatestContentMediaIdsByMemberIdAndSeriesMediaIds(memberId, seriesMediaIdList);

        // 3) 시청 이력 없는 시리즈 → 1화 fallback 일괄 조회 (1회)
        List<Long> noHistorySeriesIdList = seriesMediaIdList.stream()
                .filter(id -> !latestEpisodeMap.containsKey(id))
                .toList();
        Map<Long, Long> firstEpisodeMap = noHistorySeriesIdList.isEmpty()
                ? new HashMap<>()
                : contentsRepository.findFirstEpisodeMediaIdsBySeriesMediaIds(noHistorySeriesIdList);

        // 4) 맵 병합
        Map<Long, Long> mediaToTargetIdMap = new HashMap<>();
        for (Media media : mediaPage.getContent()) {
            if (media.getMediaType() == MediaType.SERIES) {
                Long targetId = latestEpisodeMap.getOrDefault(media.getId(),
                        firstEpisodeMap.get(media.getId()));
                mediaToTargetIdMap.put(media.getId(), targetId);
            } else {
                mediaToTargetIdMap.put(media.getId(), media.getId());
            }
        }
        
        // List<Long> targetMediaIds = new ArrayList<>(mediaToTargetIdMap.values());
        List<Long> targetMediaIds = mediaToTargetIdMap.values().stream()
                                    .filter(java.util.Objects::nonNull)
                                    .distinct()
                                    .toList();

        
        // 재생 시간(duration) 맵 세팅
        final Map<Long, Integer> durationMap = targetMediaIds.isEmpty() ? new HashMap<>() : 
            contentsRepository.findAllByMediaIdIn(targetMediaIds).stream()
                .collect(Collectors.toMap(
                        c -> c.getMedia().getId(), 
                        c -> c.getDuration() != null ? c.getDuration() : 0, 
                        (existing, replacement) -> existing
                ));
                
        // 이어보기 지점(positionSec) 맵 세팅 
        final Map<Long, Integer> playbackMap = (memberId == null || targetMediaIds.isEmpty()) ? new HashMap<>() :
            playbackRepository.findAllByMemberIdAndMediaIds(memberId, targetMediaIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getContents().getMedia().getId(), 
                        p -> p.getPositionSec() != null ? p.getPositionSec() : 0,
                        (existing, replacement) -> existing
                ));
      
        // 3. Entity -> DTO 변환
        List<PlaylistResponse> contentList = mediaPage.getContent().stream()
                .map(media -> {
                    Long targetId = mediaToTargetIdMap.get(media.getId());
                    // targetId가 null인 경우(시리즈인데 콘텐츠가 하나도 없는 예외상황) 방어
                    Integer duration = targetId != null ? durationMap.getOrDefault(targetId, 0) : 0;
                    Integer positionSec = targetId != null ? playbackMap.getOrDefault(targetId, 0) : 0;

                    return PlaylistResponse.from(media, duration, positionSec);
                })
                .toList();


        // 4. SliceInfo 생성
        SliceInfo sliceInfo = SliceInfo.builder()
                .currentPage(mediaPage.getNumber())
                .pageSize(mediaPage.getSize())
                .hasNext(mediaPage.hasNext())
                .build();

        return SliceResponse.toSliceResponse(sliceInfo, contentList);
    }


    public TopTagPlaylistResponse getTopTagPlaylistWithMetadata(PlaylistCondition condition, Pageable pageable){
        //상위 태그 먼저 가져오기
        List<Tag> topTags = preferenceService.getTopTags(condition.getMemberId());
        
        TopTagPlaylistResponse.CategoryInfo categoryInfo = null;
        TopTagPlaylistResponse.TagInfo tagInfo = null;

    
       if (condition.getIndex() != null && condition.getIndex() >= 0 && condition.getIndex() < topTags.size()) {
            Tag targetTag = topTags.get(condition.getIndex());

            // 상위 태그 조립해줌
            condition.setTagId(targetTag.getId());
            
            // TagInfo 객체 조립
            tagInfo = TopTagPlaylistResponse.TagInfo.builder()
                    .id(targetTag.getId())
                    .name(targetTag.getName())
                    .build();

            // CategoryInfo 객체 조립
            if (targetTag.getCategory() != null) {
                categoryInfo = TopTagPlaylistResponse.CategoryInfo.builder()
                        .id(targetTag.getCategory().getId())
                        .name(targetTag.getCategory().getName())
                        .build();
            }
        }

        // 상위태그가 조립된 상태로 플레이리스트 조회
        SliceResponse<PlaylistResponse> mediaPage = getPlaylists(condition, pageable);


        return TopTagPlaylistResponse.builder()
                    .category(categoryInfo)
                    .tag(tagInfo)
                    .medias(mediaPage)
                    .build();
    }



    private PlaylistStrategy getStrategy(PlaylistCondition condition) {
        String strategyKey = determineStrategyKey(condition);
        PlaylistStrategy strategy = strategyMap.get(strategyKey);

        if (strategy == null) {
            strategy = strategyMap.get(ContentSource.RECOMMEND.name());
        }
        
        // 여전히 null이라면 시스템 설정 오류이므로 S001 에러 발생
        if (strategy == null) {
            throw new BusinessException(ErrorCode.STRATEGY_NOT_FOUND);
        }
        return strategy;
    }



    private String determineStrategyKey(PlaylistCondition condition) {
        ContentSource source = condition.getContentSource();

        // 검색 결과에서 상세로 진입한 시 재생목록은 추천으로 대체
        if (source == ContentSource.SEARCH && condition.getExcludeMediaId() != null) {
            return ContentSource.RECOMMEND.name();
        }
        return source.name();
    }


    // [Before] 시리즈 1화의 MediaId를 가져오는 헬퍼 메서드 (N+1 원인 - Page 반환으로 COUNT 쿼리까지 발생)
    // private Long getFirstEpisodeMediaId(Long seriesId) {
    //     Pageable limitOne = PageRequest.of(0, 1);
    //     Page<Contents> firstContentPage = contentsRepository
    //             .findBySeries_Media_IdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(seriesId, Status.ACTIVE, PublicStatus.PUBLIC, MediaStatus.COMPLETED, limitOne);
    //     if (firstContentPage.isEmpty()) {
    //         return null;
    //     }
    //     return firstContentPage.getContent().get(0).getMedia().getId();
    // }


}