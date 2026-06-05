package com.ott.domain.media.repository;

import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.media.domain.Media;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;


public interface MediaRepositoryCustom {

        Page<Media> findMediaListByMediaTypeAndSearchWord(Pageable pageable, MediaType mediaType, String searchWord);

        Page<Media> findMediaListByMediaTypeAndSearchWordAndPublicStatus(Pageable pageable, MediaType mediaType,
                        String searchWord, PublicStatus publicStatus);

        Page<Media> findMediaListByMediaTypeAndSearchWordAndPublicStatusAndUploaderId(Pageable pageable,
                        MediaType mediaType, String searchWord, PublicStatus publicStatus, Long uploaderId);

        Page<Media> findOriginMediaListBySearchWord(Pageable pageable, String searchWord);

        List<TagContentProjection> findRecommendContentsByTagId(Long tagId, int limit);

         // 특정 태그 기반 미디어 목록 조회
        List<Media> findMediasByTagId(Long tagId,Long excludeMediaId, int limit , long offset);

       // 인기 차트 통합 조회 메서드
        Slice<Media> findTrendingPlaylists(MediaType mediaType, Long excludeMediaId, Pageable pageable);

        // 시청 이력 조회 (최근 시청 순)
        Slice<Media> findHistoryPlaylists(Long memberId, MediaType mediaType, Long excludeMediaId, Pageable pageable);

        // 북마크 목록 조회 (최근 찜한 순)
        Slice<Media> findBookmarkedPlaylists(Long memberId, MediaType mediaType, Long excludeMediaId, Pageable pageable);

        // 특정 태그 기반 미디어 목록 조회 (페이징 객체 사용)
        Slice<Media> findPlaylistsByTag(Long tagId, MediaType mediaType, Long excludeMediaId, Pageable pageable);

        // 특정 태그 기반 미디어 목록 조회 (limit, offset 사용)
        List<Media> findMediasByTagId(Long tagId, MediaType mediaType, Long excludeMediaId, int limit , long offset);

        // 추천 종합 쿼리
        List<Media> findRecommendedMedias(Map<Long, Integer> tagScores, MediaType mediaType, Long excludeMediaId, int limit, long offset);


        // 유저용 통합 검색 (활성 상태 및 공개 처리된 시리즈+단편만 조회)
        Page<Media> findUserSearchMediaList(Pageable pageable, String searchWord);


        // mood AI 가 추천한 타겟 태그 이름을 기반으로 영상 추출
        List<Media> findByTop3ByMoodTagName(String tagName);
}