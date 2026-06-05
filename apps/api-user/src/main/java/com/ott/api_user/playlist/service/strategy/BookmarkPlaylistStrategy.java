package com.ott.api_user.playlist.service.strategy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.repository.MediaRepository;

// 북마크 목록 - 특정 회원이 북마크한 미디어 리스트
@Component("BOOKMARK")
@RequiredArgsConstructor
public class BookmarkPlaylistStrategy implements PlaylistStrategy {
    private final MediaRepository mediaRepository;

    @Override
    public Slice<Media> getPlaylist(PlaylistCondition condition, Pageable pageable) {
        
        return mediaRepository.findBookmarkedPlaylists(
            condition.getMemberId(), 
            condition.getMediaType(),
            condition.getExcludeMediaId(), 
            pageable
        );
    }
}
