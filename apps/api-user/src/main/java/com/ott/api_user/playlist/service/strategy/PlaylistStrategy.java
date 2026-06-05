package com.ott.api_user.playlist.service.strategy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.domain.media.domain.Media;

//공통 인터페이스

// 기존의 swtich 문으로 작성해둔 진입 시점에 따른 분기처리를
// 전략 패턴을 도입하여 공통으로
public interface PlaylistStrategy {
  /**
     * 조건에 맞는 미디어 목록을 찾아옵니다.
     * @param condition 프론트엔드에서 넘어온 진입 시점
     * @param pageable  페이징 정보
     * @return DB에서 찾아온 Media 엔티티들의 슬라이스 객체
     */
    Slice<Media> getPlaylist(PlaylistCondition condition, Pageable pageable);
}