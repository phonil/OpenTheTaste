package com.ott.domain.contents.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.media.domain.MediaStatus;


public interface ContentsRepository extends JpaRepository<Contents, Long>, ContentsRepositoryCustom {

        @EntityGraph(attributePaths = { "media" })
        Page<Contents> findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(
                        Long seriesId, Status status, PublicStatus publicStatus, MediaStatus mediaStatus,
                        Pageable pageable);

        @EntityGraph(attributePaths = { "media" })
        Page<Contents> findBySeries_Media_IdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(
                        Long seriesMediaId, Status status, PublicStatus publicStatus, MediaStatus mediaStatus,
                        Pageable pageable);

        // 좋아요 처리 시 series 소속 여부 확인용
        @EntityGraph(attributePaths = {"series", "series.media"})
        Optional<Contents> findByMediaId(Long mediaId);

        // 댓글 작성 시 콘텐츠 조회
        @EntityGraph(attributePaths = {"media"})
        Optional<Contents> findByIdAndStatus(Long id, Status status);

        
        // 미디어 Id 로 해당 콘텐츠 조회 
        @Query("""
                SELECT c FROM Contents c
                JOIN FETCH c.media m
                WHERE m.id = :mediaId
                AND c.status = :status
                AND m.publicStatus = :publicStatus
                AND m.mediaStatus = com.ott.domain.media.domain.MediaStatus.COMPLETED
                """)
        Optional<Contents> findByMediaIdAndStatusAndMedia_PublicStatus(
                @Param("mediaId") Long mediaId,
                @Param("status") Status status,
                @Param("publicStatus") PublicStatus publicStatus);

        @Query("""
                SELECT c.id
                FROM Contents c
                JOIN c.media m
                WHERE m.id = :mediaId
                AND c.status = com.ott.domain.common.Status.ACTIVE
                AND m.publicStatus = com.ott.domain.common.PublicStatus.PUBLIC
                AND m.mediaStatus = com.ott.domain.media.domain.MediaStatus.COMPLETED
                """)
        Optional<Long> findPlayableContentsIdByMediaId(@Param("mediaId") Long mediaId);

        /**
         * @deprecated Playback buffering 구조에서는 앞단 캐시 검증 후 contentsId 기반 upsert를 사용한다.
         *             신규 경로는 findPlayableContentsIdByMediaId(Long)를 사용한다.
         */
        @Deprecated
        @Query("""
                SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
                FROM Contents c
                JOIN c.media m
                WHERE m.id = :mediaId
                AND c.status = com.ott.domain.common.Status.ACTIVE
                AND m.publicStatus = com.ott.domain.common.PublicStatus.PUBLIC
                AND m.mediaStatus = com.ott.domain.media.domain.MediaStatus.COMPLETED
                """)
        boolean existsPlayableByMediaId(@Param("mediaId") Long mediaId);
}
