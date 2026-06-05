package com.ott.domain.watch_history.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ott.domain.watch_history.domain.WatchHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WatchHistoryRepositoryCustom {

    List<WatchHistory> findRecentUnusedHistoriesWithin(Long memberId, LocalDateTime cutoff, int limit);

    List<TagViewCountProjection> countByTagAndCategoryIdAndWatchedBetween(Long categoryId, LocalDateTime startDate, LocalDateTime endDate);

    //특정 회원의 최근 1달 시청이력 기반 태그 집계 (count 내림차순)
    List<TagRankingProjection> findTopTagsByMemberIdAndWatchedBetween(Long memberId, LocalDateTime startDate, LocalDateTime endDate);

    // 특정 태그의 2달 시청이력 기반 count 집계
    Long countByMemberIdAndTagIdAndWatchedBetween(Long memberId, Long tagId, LocalDateTime startDate, LocalDateTime endDate);

    // 특정 회원의 전체 시청이력 페이징 조회 (최신순)
    Page<RecentWatchProjection> findWatchHistoryByMemberId(Long memberId, Pageable pageable);

    // 가장 최근에 시청한 에피소드의 media_id 반환
    Optional<Long> findLatestContentMediaIdByMemberIdAndSeriesId(Long memberId, Long seriesId);


    Optional<Long> findLatestContentMediaIdByMemberIdAndSeriesMediaId(Long memberId, Long seriesMediaId);

    // [4-1] 시리즈 ID 목록 → 시리즈별 최근 시청 에피소드 Media ID 일괄 조회 (N+1 해결)
    Map<Long, Long> findLatestContentMediaIdsByMemberIdAndSeriesMediaIds(Long memberId, List<Long> seriesMediaIdList);
}
