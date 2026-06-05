package com.ott.api_user.playlist.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import lombok.RequiredArgsConstructor;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.api_user.playlist.service.PlaylistPreferenceService;
import com.ott.common.web.exception.BusinessException;
import com.ott.common.web.exception.ErrorCode;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;
import com.ott.domain.tag.domain.Tag;

/**
 * 태그별 플레이리스트 전략
 * 특정 태그 ID를 기준으로 관련 미디어 목록을 제공합니다.
 */
@Component("TAG")
@RequiredArgsConstructor
public class TagPlaylistStrategy implements PlaylistStrategy {

    private final PlaylistPreferenceService preferenceService;
    private final MediaRepository mediaRepository;

    @Override
    public Slice<Media> getPlaylist(PlaylistCondition condition, Pageable pageable) {
        Long targetTagId = condition.getTagId();

        // 홈화면인지 재생목록인지 구분함
        boolean isHomeScreen = (condition.getExcludeMediaId() == null);

        // 1. 명시적인 tagId 없이 index만 넘어온 경우 (특정 태그별 리스트를 홈 화면에 노출 시키고 싶을 때 재사용)
        if (targetTagId == null && condition.getIndex() != null) {

            // 유저의 취향 Top 3 태그를 계산해서 가져옴
            List<Tag> topTags = preferenceService.getTopTags(condition.getMemberId());
            int index = condition.getIndex();
            
            // 프론트가 요청한 순위(index)의 태그 ID를 타겟으로 설정
            if (index >= 0 && condition.getIndex() < topTags.size()) {
                targetTagId = topTags.get(condition.getIndex()).getId();
            } else {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }

        if(targetTagId == null){
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 2. 모수 풀링: 홈 화면(page=0)은 섞기 위해 50개를 넉넉히 가져오고, 상세 페이지는 요구한 만큼만 가져옴
        int fetchLimit = (pageable.getPageNumber() == 0 && isHomeScreen) ? 50 : pageable.getPageSize();
        
        // 홈 화면은 항상 무작위로 섞을 거니까 0으로 고정,  상세 페이지는 페이지에 맞게 건너뜀
        long fetchOffset = (isHomeScreen) ? 0 : pageable.getOffset();
        
        List<Media> mediaPool = mediaRepository.findMediasByTagId(targetTagId, condition.getMediaType(), condition.getExcludeMediaId(), fetchLimit, fetchOffset);

        // 3. 디스커버리 UX: 홈 화면일 경우에만 매번 새로운 콘텐츠를 발견하도록 리스트를 무작위로 섞음
        if (pageable.getPageNumber() == 0 && isHomeScreen) {
            Collections.shuffle(mediaPool);
        }

        // 4. 프론트가 요구한 사이즈(예: 20개)만큼만 잘라서 Page 객체로 포장 후 반환
        int limit = Math.min(mediaPool.size(), pageable.getPageSize());

        boolean hasNext = mediaPool.size() > limit;
        return new SliceImpl<>(mediaPool.subList(0, limit), pageable, hasNext);
    }
}