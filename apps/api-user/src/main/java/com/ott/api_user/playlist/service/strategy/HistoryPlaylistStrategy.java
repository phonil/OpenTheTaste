package com.ott.api_user.playlist.service.strategy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;

//시청 이력 기반 플레이리스트 - playback (시청기록)을 토대로 중복 제거 후 최근날짜 순 리스트
@Component("HISTORY")
@RequiredArgsConstructor
public class HistoryPlaylistStrategy implements PlaylistStrategy {
    private final MediaRepository mediaRepository;

    @Override
    public Slice<Media> getPlaylist(PlaylistCondition condition, Pageable pageable) {
        
        return mediaRepository.findHistoryPlaylists(
            condition.getMemberId(), 
            condition.getMediaType(),
            condition.getExcludeMediaId(), 
            pageable
        );
    }
}