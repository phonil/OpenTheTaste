package com.ott.api_user.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.common.ContentSource;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.api_user.playlist.dto.response.PlaylistResponse;
import com.ott.api_user.playlist.dto.response.TopTagPlaylistResponse;
import com.ott.api_user.playlist.service.strategy.PlaylistStrategy;
import com.ott.common.web.exception.BusinessException;
import com.ott.common.web.exception.ErrorCode;
import com.ott.common.web.response.SliceInfo;
import com.ott.common.web.response.SliceResponse;
import com.ott.domain.category.domain.Category;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.common.Status;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.contents.repository.ContentsRepository;
import com.ott.domain.media.domain.Media;
import com.ott.domain.media.domain.MediaStatus;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.playback.domain.Playback;
import com.ott.domain.playback.repository.PlaybackRepository;
import com.ott.domain.watch_history.repository.WatchHistoryRepository;
import com.ott.domain.tag.domain.Tag;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// 전략 패턴을 활용하여 , 플레이리스트 요청 타입(검색, 추천, 트렌딩) 등에 따라 알맞은 로직을 수행하고
// 영상의 메타데이터를 덧붙이는 복합적인 서비스 로직을 검증

@ExtendWith(MockitoExtension.class)
class PlaylistStrategyServiceTest {

    @Mock
    private Map<String, PlaylistStrategy> strategyMap;

    @Mock
    private PlaylistStrategy recommendStrategy;

    @Mock
    private PlaylistPreferenceService preferenceService;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private PlaybackRepository playbackRepository;

    @Spy
    @InjectMocks
    private PlaylistStrategyService playlistStrategyService;

    @Test
    void getPlaylists_throwWhenContentSourceMissing() {
        PlaylistCondition condition = new PlaylistCondition();

        assertThatThrownBy(() -> playlistStrategyService.getPlaylists(condition, PageRequest.of(0, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PLAYLIST_SOURCE);
    }

    @Test
    void getPlaylists_appliesRecommendStrategyAndMapsDurationAndPlayback() {
        PlaylistCondition condition = new PlaylistCondition();
        condition.setContentSource(ContentSource.SEARCH);
        condition.setExcludeMediaId(99L);
        condition.setMemberId(5L);

        Pageable pageable = PageRequest.of(0, 1);
        Media seriesMedia = createMedia(10L, MediaType.SERIES);
        Page<Media> page = new PageImpl<>(List.of(seriesMedia), pageable, 1);

        when(strategyMap.get(ContentSource.RECOMMEND.name())).thenReturn(recommendStrategy);
        when(recommendStrategy.getPlaylist(condition, pageable)).thenReturn(page);
        when(watchHistoryRepository.findLatestContentMediaIdsByMemberIdAndSeriesMediaIds(condition.getMemberId(), List.of(seriesMedia.getId())))
                .thenReturn(Map.of(seriesMedia.getId(), 20L));

        Contents targetContents = createContents(20L, 333);
        when(contentsRepository.findAllByMediaIdIn(List.of(20L))).thenReturn(List.of(targetContents));

        Member playbackMember = createMember(condition.getMemberId());
        when(playbackRepository.findAllByMemberIdAndMediaIds(condition.getMemberId(), List.of(20L)))
                .thenReturn(List.of(createPlayback(playbackMember, targetContents, 123)));

        SliceResponse<PlaylistResponse> result = playlistStrategyService.getPlaylists(condition, pageable);
        PlaylistResponse response = result.getDataList().get(0);

        assertThat(response.getDuration()).isEqualTo(333);
        assertThat(response.getPositionSec()).isEqualTo(123);
    }

    @Test
    void getPlaylists_handlesSeriesWithoutAvailableContentGracefully() {
        PlaylistCondition condition = new PlaylistCondition();
        condition.setContentSource(ContentSource.TRENDING);
        condition.setMemberId(7L);

        Pageable pageable = PageRequest.of(0, 1);
        Media seriesMedia = createMedia(11L, MediaType.SERIES);
        when(strategyMap.get(ContentSource.TRENDING.name())).thenReturn(recommendStrategy);
        when(recommendStrategy.getPlaylist(condition, pageable)).thenReturn(new PageImpl<>(List.of(seriesMedia), pageable, 1));
        when(watchHistoryRepository.findLatestContentMediaIdsByMemberIdAndSeriesMediaIds(condition.getMemberId(), List.of(seriesMedia.getId())))
                .thenReturn(Map.of());
        when(contentsRepository.findFirstEpisodeMediaIdsBySeriesMediaIds(List.of(seriesMedia.getId())))
                .thenReturn(Map.of());

        SliceResponse<PlaylistResponse> result = playlistStrategyService.getPlaylists(condition, pageable);
        PlaylistResponse response = result.getDataList().get(0);

        assertThat(response.getDuration()).isZero();
        assertThat(response.getPositionSec()).isZero();
        verify(contentsRepository, never()).findAllByMediaIdIn(anyList());
    }

    @Test
    void getTopTagPlaylistWithMetadata_setsTagAndCategoryInfo() {
        PlaylistCondition condition = new PlaylistCondition();
        condition.setContentSource(ContentSource.RECOMMEND);
        condition.setMemberId(13L);
        condition.setIndex(0);

        Pageable pageable = PageRequest.of(0, 5);
        Category category = Category.builder().id(77L).name("genre").build();
        Tag tag = Tag.builder().id(99L).category(category).name("drama").build();

        when(preferenceService.getTopTags(condition.getMemberId())).thenReturn(List.of(tag));

        SliceResponse<PlaylistResponse> fakePage = SliceResponse.toSliceResponse(
                SliceInfo.builder().currentPage(0).pageSize(5).hasNext(false).build(),
                Collections.emptyList()
        );

        doReturn(fakePage).when(playlistStrategyService).getPlaylists(condition, pageable);

        TopTagPlaylistResponse response = playlistStrategyService.getTopTagPlaylistWithMetadata(condition, pageable);

        assertThat(response.getTag()).isNotNull();
        assertThat(response.getTag().getId()).isEqualTo(tag.getId());
        assertThat(response.getCategory().getName()).isEqualTo(category.getName());
        assertThat(response.getMedias()).isSameAs(fakePage);
        assertThat(condition.getTagId()).isEqualTo(tag.getId());
    }

    private static Media createMedia(Long id, MediaType mediaType) {
        return Media.builder()
                .id(id)
                .uploader(createMember(-1L))
                .title("title-" + id)
                .description("desc")
                .posterUrl("poster-" + id)
                .thumbnailUrl("thumb-" + id)
                .bookmarkCount(0L)
                .likesCount(0L)
                .mediaType(mediaType)
                .publicStatus(PublicStatus.PUBLIC)
                .mediaStatus(MediaStatus.COMPLETED)
                .build();
    }

    private static Member createMember(Long id) {
        return Member.builder()
                .id(id)
                .email("member-" + id + "@ott")
                .nickname("member-" + id)
                .provider(Provider.KAKAO)
                .role(Role.MEMBER)
                .build();
    }

    private static Contents createContents(Long mediaId, Integer duration) {
        return Contents.builder()
                .id(mediaId)
                .media(createMedia(mediaId, MediaType.CONTENTS))
                .actors("actors")
                .duration(duration)
                .videoSize(1)
                .originUrl("origin")
                .masterPlaylistUrl("master")
                .build();
    }

    private static Playback createPlayback(Member member, Contents contents, Integer positionSec) {
        return Playback.builder()
                .member(member)
                .contents(contents)
                .positionSec(positionSec)
                .build();
    }
}
