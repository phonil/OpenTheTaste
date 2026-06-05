package com.ott.domain.playback.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ott.domain.common.Status;
import com.ott.domain.playback.domain.Playback;
import org.springframework.data.jpa.repository.Modifying;

public interface PlaybackRepository extends JpaRepository<Playback, Long>, PlaybackRepositoryCustom {

        // 회원 탈퇴
        @Modifying(clearAutomatically = true)
        @Query("UPDATE Playback p SET p.status = 'DELETE' WHERE p.member.id = :memberId")
        void softDeleteAllByMemberId(@Param("memberId") Long memberId);


        // 최근 시청한 미디어의 태그 ID 조회
        // 1단계 - 최근 시청 이력 100개 가져오기 (JOIN 보다 LIMIT 먼저)
        @Query("""
                SELECT p.contents.media.id FROM Playback p
                WHERE p.member.id = :memberId AND p.status = :status
                ORDER BY p.modifiedDate DESC
                """)
        List<Long> findRecentPlayedMediaIds(
                @Param("memberId") Long memberId,
                @Param("status") Status status,
                Pageable pageable);


        //현재 플레이리스트의 모든 미디어에 대한 일괄 조회
        @Query("""
                SELECT p FROM Playback p
                WHERE p.member.id = :memberId
                AND p.contents.media.id IN :mediaIdList
                AND p.status = 'ACTIVE'
                ORDER BY p.modifiedDate DESC
                """)
        List<Playback> findAllByMemberIdAndMediaIds(
            @Param("memberId") Long memberId,
            @Param("mediaIdList") List<Long> mediaIdList);

        
        // 단건 조회(컨텐츠 or 에피별) 에 대해 시청기록 조회
        @Query("""
                SELECT p FROM Playback p
                WHERE p.member.id = :memberId
                AND p.contents.media.id = :mediaId
                AND p.status = 'ACTIVE'
                """)
        Optional<Playback> findByMemberIdAndMediaId(
                @Param("memberId") Long memberId,
                @Param("mediaId") Long mediaId);
        
        
        /**
         * @deprecated Playback start/update 분리 구조에서는 init 시 insertIgnorePlayback(Long, Long),
         *             progress 갱신 시 updatePlayback(Long, Long, int)를 사용한다.
         */
        @Deprecated
        @Modifying
        @Query(value = """
                INSERT INTO playback (member_id, contents_id, position_sec, created_date, modified_date, status)
                VALUES (:memberId, :contentsId, :positionSec, NOW(), NOW(), 'ACTIVE')
                ON DUPLICATE KEY UPDATE 
                        position_sec = :positionSec,
                        modified_date = NOW()
                """, nativeQuery = true)
        void upsertPlayback(
                @Param("memberId") Long memberId, 
                @Param("contentsId") Long contentsId, 
                @Param("positionSec") int positionSec);

        @Modifying
        @Query(value = """
                UPDATE playback
                SET position_sec = :positionSec,
                    modified_date = NOW()
                WHERE member_id = :memberId
                  AND contents_id = :contentsId
                  AND status = 'ACTIVE'
                """, nativeQuery = true)
        int updatePlayback(
                @Param("memberId") Long memberId,
                @Param("contentsId") Long contentsId,
                @Param("positionSec") int positionSec);

        @Modifying
        @Query(value = """
                INSERT IGNORE INTO playback (member_id, contents_id, position_sec, event_time, created_date, modified_date, status)
                VALUES (:memberId, :contentsId, 0, NOW(), NOW(), NOW(), 'ACTIVE')
                """, nativeQuery = true)
        int insertIgnorePlayback(
                @Param("memberId") Long memberId,
                @Param("contentsId") Long contentsId);

        /**
         * @deprecated Playback start/update 분리 구조에서는 init 시 insertIgnorePlayback(Long, Long),
         *             progress 갱신 시 updatePlayback(Long, Long, int)를 사용한다.
         */
        @Deprecated
        @Modifying
        @Query(value = """
                INSERT INTO playback (member_id, contents_id, position_sec, created_date, modified_date, status)
                SELECT :memberId, c.id, :positionSec, NOW(), NOW(), 'ACTIVE'
                FROM contents c
                JOIN media m ON m.id = c.media_id
                WHERE m.id = :mediaId
                  AND c.status = 'ACTIVE'
                  AND m.public_status = 'PUBLIC'
                  AND m.media_status = 'COMPLETED'
                ON DUPLICATE KEY UPDATE
                    position_sec = :positionSec,
                    modified_date = NOW()
                """, nativeQuery = true)
        int upsertPlaybackByMediaId(
                @Param("memberId") Long memberId,
                @Param("mediaId") Long mediaId,
                @Param("positionSec") int positionSec);
}
