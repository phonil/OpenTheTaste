package com.ott.api_user.playlist.service.strategy;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.api_user.playlist.service.PlaylistPreferenceService;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;


// 개인화 추천 - 시청이력 + 좋아요 + 기존 선호 태그
@Component("RECOMMEND")
@RequiredArgsConstructor
public class RecommendPlaylistStrategy  implements PlaylistStrategy  {
    
    private final PlaylistPreferenceService preferenceService;
    private final MediaRepository mediaRepository;

    @Override
    public Slice<Media> getPlaylist(PlaylistCondition condition, Pageable pageable) {
        
        boolean isHomeScreen = (condition.getExcludeMediaId() == null);

        // 유저의 모든 행동(+5, +3, +2)이 합산된 종합 점수표(Map)를 가져옴
        Map<Long, Integer> tagScores = preferenceService.getTotalTagScores(condition.getMemberId());

        int fetchLimit = (pageable.getPageNumber() == 0 && isHomeScreen) ? 50 : pageable.getPageSize();

        long fetchOffset = (isHomeScreen) ? 0 : pageable.getOffset();
        
        
        // QueryDSL CaseBuilder 쿼리 실행 -> DB 내부에서 점수 합산 후 내림차순 정렬된 리스트 반환
        List<Media> mediaPool = mediaRepository.findRecommendedMedias(
                tagScores, 
                condition.getMediaType(),
                condition.getExcludeMediaId(), 
                fetchLimit, 
                fetchOffset 
        );

        if (pageable.getPageNumber() == 0 && isHomeScreen) {
            Collections.shuffle(mediaPool);
        }


        int limit = Math.min(mediaPool.size(), pageable.getPageSize());
        boolean hasNext = mediaPool.size() > limit;
        return new SliceImpl<>(mediaPool.subList(0, limit), pageable, hasNext);
    }
    
}
