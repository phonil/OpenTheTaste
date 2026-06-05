package com.ott.domain.contents.repository;

import com.ott.domain.contents.domain.Contents;
import com.ott.domain.contents.domain.QContents;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.media.domain.QMedia;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ott.domain.contents.domain.QContents.contents;
import static com.ott.domain.media.domain.QMedia.media;
import static com.ott.domain.member.domain.QMember.member;
import static com.ott.domain.series.domain.QSeries.series;

@RequiredArgsConstructor
public class ContentsRepositoryImpl implements ContentsRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Contents> findWithMediaById(Long contentsId) {
        Contents result = queryFactory
                .selectFrom(contents)
                .join(contents.media, media).fetchJoin()
                .where(
                        contents.id.eq(contentsId),
                        media.mediaStatus.eq(MediaStatus.COMPLETED))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<Contents> findAllByMediaIdIn(List<Long> mediaIdList) {
        return queryFactory
                .selectFrom(contents)
                .where(contents.media.id.in(mediaIdList))
                .fetch();
    }

    // 가정: 회차(episodeNumber) 컬럼이 없으므로 "가장 늦게 생성된 에피소드 = 마지막 화"로 간주.
    // createdDate 동률 시 id가 큰 쪽을 선택.
    @Override
    public List<Contents> findLastEpisodeBySeriesMediaIds(List<Long> seriesMediaIdList) {
        QMedia seriesMedia = new QMedia("seriesMedia");
        QContents sub = new QContents("sub");
        QContents sub2 = new QContents("sub2");

        return queryFactory
                .selectFrom(contents)
                .join(contents.series, series).fetchJoin()
                .join(series.media, seriesMedia).fetchJoin()
                .where(
                        seriesMedia.id.in(seriesMediaIdList),
                        contents.id.eq(
                                JPAExpressions
                                        .select(sub.id.max())
                                        .from(sub)
                                        .where(
                                                sub.series.id.eq(series.id),
                                                sub.createdDate.eq(
                                                        JPAExpressions
                                                                .select(sub2.createdDate.max())
                                                                .from(sub2)
                                                                .where(sub2.series.id.eq(series.id))
                                                )
                                        )
                        )
                )
                .fetch();
    }

    // [4-2] 시리즈 ID 목록 → 시리즈별 1화 Media ID 일괄 조회 (N+1 해결)
    // findLastEpisodeBySeriesMediaIds와 동일 구조, min()으로 1화 조회
    @Override
    public Map<Long, Long> findFirstEpisodeMediaIdsBySeriesMediaIds(List<Long> seriesMediaIdList) {
        if (seriesMediaIdList.isEmpty()) {
            return new HashMap<>();
        }

        QMedia seriesMedia = new QMedia("seriesMedia");
        QContents sub = new QContents("sub");
        QContents sub2 = new QContents("sub2");

        List<Contents> resultList = queryFactory
                .selectFrom(contents)
                .join(contents.media, media).fetchJoin()
                .join(contents.series, series).fetchJoin()
                .join(series.media, seriesMedia).fetchJoin()
                .where(
                        seriesMedia.id.in(seriesMediaIdList),
                        contents.status.eq(com.ott.domain.common.Status.ACTIVE),
                        media.publicStatus.eq(com.ott.domain.common.PublicStatus.PUBLIC),
                        media.mediaStatus.eq(MediaStatus.COMPLETED),
                        contents.id.eq(
                                JPAExpressions
                                        .select(sub.id.min())
                                        .from(sub)
                                        .where(
                                                sub.series.id.eq(series.id),
                                                sub.status.eq(com.ott.domain.common.Status.ACTIVE),
                                                sub.createdDate.eq(
                                                        JPAExpressions
                                                                .select(sub2.createdDate.min())
                                                                .from(sub2)
                                                                .where(
                                                                        sub2.series.id.eq(series.id),
                                                                        sub2.status.eq(com.ott.domain.common.Status.ACTIVE)
                                                                )
                                                )
                                        )
                        )
                )
                .fetch();

        Map<Long, Long> resultMap = new HashMap<>();
        for (Contents c : resultList) {
            resultMap.put(c.getSeries().getMedia().getId(), c.getMedia().getId());
        }
        return resultMap;
    }

    @Override
    public Optional<Contents> findWithMediaAndUploaderByMediaId(Long mediaId) {
        QMedia seriesMedia = new QMedia("seriesMedia");
        Contents result = queryFactory
                .selectFrom(contents)
                .join(contents.media, media).fetchJoin()
                .join(media.uploader, member).fetchJoin()
                .leftJoin(contents.series, series).fetchJoin()
                .leftJoin(series.media, seriesMedia).fetchJoin()
                .where(
                        media.id.eq(mediaId),
                        media.mediaStatus.eq(MediaStatus.COMPLETED)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
