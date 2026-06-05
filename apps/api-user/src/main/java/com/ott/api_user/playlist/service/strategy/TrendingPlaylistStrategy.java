package com.ott.api_user.playlist.service.strategy;


import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;

import lombok.RequiredArgsConstructor;

// 인기 차트 플레이리스트 - 북마크 순으로 내림차순 정렬 리스트
@Component("TRENDING") // ContentSource Enum 이름과 똑같이 맞춤
@RequiredArgsConstructor
public class TrendingPlaylistStrategy implements PlaylistStrategy {
    private final MediaRepository mediaRepository;

    @Override
    public Slice<Media> getPlaylist(PlaylistCondition condition, Pageable pageable) {
        // 리포지토리 내부에서 excludeMediaId의 유무를 알아서 판단하여 처리합니다.
        return mediaRepository.findTrendingPlaylists(
            condition.getMediaType(),
            condition.getExcludeMediaId(), 
            pageable
        );
    }
}
