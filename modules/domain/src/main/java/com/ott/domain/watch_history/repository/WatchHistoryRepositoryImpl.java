package com.ott.domain.watch_history.repository;

import com.ott.domain.common.Status;
import com.ott.domain.watch_history.domain.WatchHistory;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ott.domain.common.Status.ACTIVE;
import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.media_tag.domain.QMediaTag.mediaTag;
import static com.ott.domain.playback.domain.QPlayback.playback;
import static com.ott.domain.tag.domain.QTag.tag;
import static com.ott.domain.watch_history.domain.QWatchHistory.watchHistory;

@RequiredArgsConstructor
public class WatchHistoryRepositoryImpl implements WatchHistoryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<TagViewCountProjection> countByTagAndCategoryIdAndWatchedBetween(Long categoryId, LocalDateTime startDate, LocalDateTime endDate) {
        return queryFactory
                .select(Projections.constructor(TagViewCountProjection.class,
                        tag.name,
                        watchHistory.count()
                ))
                .from(tag)
                .join(mediaTag).on(mediaTag.tag.id.eq(tag.id))
                .join(contents).on(contents.media.id.eq(mediaTag.media.id))
                .join(watchHistory).on(watchHistory.contents.id.eq(contents.id))
                .where(
                        tag.category.id.eq(categoryId),
                        watchHistory.lastWatchedAt.goe(startDate),
                        watchHistory.lastWatchedAt.lt(endDate)
                )
                .groupBy(tag.id, tag.name)
                .fetch();
    }


    // 특정 회원의 1달 시청이력 기반 태그 집계
    @Override
    public List<TagRankingProjection> findTopTagsByMemberIdAndWatchedBetween(
            Long memberId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return queryFactory
                .select(Projections.constructor(TagRankingProjection.class,
                        tag.id,
                        tag.name,
                        watchHistory.count()
                ))
                .from(watchHistory)
                .join(contents).on(watchHistory.contents.id.eq(contents.id))
                .join(mediaTag).on(mediaTag.media.id.eq(contents.media.id)
                        .and(mediaTag.status.eq(ACTIVE)))
                .join(tag).on(tag.id.eq(mediaTag.tag.id)
                        .and(tag.status.eq(ACTIVE)))
                .where(
                        watchHistory.member.id.eq(memberId),
                        watchHistory.status.eq(ACTIVE), // delete 된거 조회 x
                        watchHistory.lastWatchedAt.goe(startDate),
                        watchHistory.lastWatchedAt.lt(endDate)
                )
                .groupBy(tag.id, tag.name)
                .orderBy(watchHistory.count().desc())
                .fetch();
    }

    // 특정 회원의 특정 태그에 대한 기간 내 시청 count
    @Override
    public Long countByMemberIdAndTagIdAndWatchedBetween(
            Long memberId,
            Long tagId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        Long result = queryFactory
                .select(watchHistory.count())
                .from(watchHistory)
                .join(contents).on(watchHistory.contents.id.eq(contents.id))
                .join(mediaTag).on(mediaTag.media.id.eq(contents.media.id)
                        .and(mediaTag.status.eq(ACTIVE)))
                .join(tag).on(tag.id.eq(mediaTag.tag.id)
                        .and(tag.status.eq(ACTIVE)))
                .where(
                        watchHistory.member.id.eq(memberId),
                        watchHistory.status.eq(ACTIVE), // delete 된거 조회 x
                        tag.id.eq(tagId),
                        watchHistory.lastWatchedAt.goe(startDate),
                        watchHistory.lastWatchedAt.lt(endDate)
                )
                .fetchOne();

        return result != null ? result : 0L;
    }

    // 특정 회원의 전체 시청이력 페이징 조회 (최신순)
    @Override
    public Page<RecentWatchProjection> findWatchHistoryByMemberId(Long memberId, Pageable pageable) {

        List<RecentWatchProjection> content = queryFactory
                .select(Projections.constructor(RecentWatchProjection.class,
                        contents.media.id,
                        contents.media.mediaType,
                        contents.media.posterUrl,
                        playback.positionSec.coalesce(0),
                        contents.duration
                ))
                .from(watchHistory)
                .join(contents).on(watchHistory.contents.id.eq(contents.id))
                .leftJoin(playback).on(
                        playback.contents.id.eq(contents.id)
                                .and(playback.member.id.eq(memberId))
                                .and(playback.status.eq(ACTIVE))
                )
                .where(
                        watchHistory.member.id.eq(memberId),
                        watchHistory.status.eq(ACTIVE) // delete 된거 조회 x
                )
                .orderBy(watchHistory.lastWatchedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(watchHistory.count())
                .from(watchHistory)
                .where(
                        watchHistory.member.id.eq(memberId),
                        watchHistory.status.eq(ACTIVE)
                        );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    
    @Override
    public Optional<Long> findLatestContentMediaIdByMemberIdAndSeriesId(Long memberId, Long seriesId){
        Long resultMediaId = queryFactory
                .select(contents.media.id)
                .from(watchHistory)
                .join(contents).on(watchHistory.contents.id.eq(contents.id))
                .where(
                        watchHistory.member.id.eq(memberId), 
                        contents.series.id.eq(seriesId),
                        watchHistory.status.eq(Status.ACTIVE)
                )
                .orderBy(watchHistory.lastWatchedAt.desc())
                .fetchFirst();
        return Optional.ofNullable(resultMediaId);
    }


    @Override
    public Optional<Long> findLatestContentMediaIdByMemberIdAndSeriesMediaId(Long memberId, Long seriesMediaId){ // 파라미터 이름도 명확하게 변경!
        Long resultMediaId = queryFactory
                .select(contents.media.id)
                .from(watchHistory)
                .join(contents).on(watchHistory.contents.id.eq(contents.id))
                .where(
                        watchHistory.member.id.eq(memberId), 
                        contents.series.media.id.eq(seriesMediaId), 
                        watchHistory.status.eq(Status.ACTIVE)
                )
                .orderBy(watchHistory.lastWatchedAt.desc())
                .fetchFirst();
        return Optional.ofNullable(resultMediaId);
    }


    
    // [4-1] 시리즈 ID 목록 → 시리즈별 최근 시청 에피소드 Media ID 일괄 조회 (N+1 해결)
    // 기존: 시리즈마다 개별 쿼리 N회 → 변경: IN절로 1회
    @Override
    public Map<Long, Long> findLatestContentMediaIdsByMemberIdAndSeriesMediaIds(Long memberId, List<Long> seriesMediaIdList) {
        if (seriesMediaIdList.isEmpty()) {
            return new HashMap<>();
        }

        // 시리즈별 가장 최근 시청 시각을 서브쿼리로 구한 뒤, 해당 행의 contents.media.id를 가져옴
        var subWh = new com.ott.domain.watch_history.domain.QWatchHistory("subWh");
        var subContents = new com.ott.domain.contents.domain.QContents("subContents");

        var resultList = queryFactory
                .select(contents.series.media.id, contents.media.id)
                .from(watchHistory)
                .join(contents).on(watchHistory.contents.id.eq(contents.id))
                .where(
                        watchHistory.member.id.eq(memberId),
                        contents.series.media.id.in(seriesMediaIdList),
                        watchHistory.status.eq(Status.ACTIVE),
                        watchHistory.lastWatchedAt.eq(
                                com.querydsl.jpa.JPAExpressions
                                        .select(subWh.lastWatchedAt.max())
                                        .from(subWh)
                                        .join(subContents).on(subWh.contents.id.eq(subContents.id))
                                        .where(
                                                subWh.member.id.eq(memberId),
                                                subContents.series.media.id.eq(contents.series.media.id),
                                                subWh.status.eq(Status.ACTIVE)
                                        )
                        )
                )
                .fetch();

        Map<Long, Long> resultMap = new HashMap<>();
        for (var tuple : resultList) {
            resultMap.put(tuple.get(contents.series.media.id), tuple.get(contents.media.id));
        }
        return resultMap;
    }

    @Override
    public List<WatchHistory> findRecentUnusedHistoriesWithin(Long memberId, LocalDateTime cutoff, int limit) {
        return queryFactory
                .selectFrom(watchHistory)
                .join(watchHistory.contents, contents).fetchJoin()
                .join(contents.media, media).fetchJoin()
                .where(
                        watchHistory.member.id.eq(memberId),
                        watchHistory.status.eq(ACTIVE),
                        watchHistory.isUsedForMl.eq(false),
                        watchHistory.lastWatchedAt.goe(cutoff) //goe: >= 의미
                )
                .orderBy(watchHistory.lastWatchedAt.desc())
                .limit(limit)
                .fetch();
    }
}
