package com.ott.api_user.series.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ott.api_user.series.dto.SeriesContentsResponse;
import com.ott.common.web.exception.BusinessException;
import com.ott.common.web.exception.ErrorCode;
import com.ott.domain.bookmark.repository.BookmarkRepository;
import com.ott.domain.category.repository.CategoryRepository;
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
import com.ott.domain.series.domain.Series;
import com.ott.domain.series.repository.SeriesRepository;
import com.ott.domain.tag.repository.TagRepository;
import com.ott.domain.watch_history.repository.WatchHistoryRepository;
import com.ott.domain.likes.repository.LikesRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeriesServiceTest {

    @Mock
    private SeriesRepository seriesRepository;

    @Mock
    private ContentsRepository contentsRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private LikesRepository likesRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Mock
    private PlaybackRepository playbackRepository;

    @InjectMocks
    private SeriesService seriesService;

    @Test
    void getSeriesContents_returnsPageResponseWithPlaybackPosition() {
        Long mediaId = 1L;
        Long memberId = 11L;
        Series series = createSeries(200L, mediaId);

        // 시리즈 조회에서 Status/공개 상태 필터링이 들어오는지 목 설정
        when(seriesRepository.findByMediaIdAndStatusAndPublicStatus(mediaId, Status.ACTIVE, PublicStatus.PUBLIC))
                .thenReturn(java.util.Optional.of(series));

        // Create two contents representing episodes with embedded media metadata
        Contents ep1 = createContents(21L, series, 120);
        Contents ep2 = createContents(22L, series, 200);
        Pageable pageable = PageRequest.of(0, 10);

        // Return a page that contains those episodes
        // 시리즈에 속한 콘텐츠만 필터링해서 반환되도록 콘텐츠 레포지토리 응답을 준비
        when(contentsRepository.findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(series.getId(), Status.ACTIVE, PublicStatus.PUBLIC, MediaStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(ep1, ep2), pageable, 2));

        // 재생 기록을 다운스트림으로 매핑하여 DTO에 positionSec이 채워지는지 확인
        when(playbackRepository.findAllByMemberIdAndMediaIds(memberId, List.of(ep1.getMedia().getId(), ep2.getMedia().getId())))
                .thenReturn(List.of(
                        createPlayback(memberId, ep1, 30),
                        createPlayback(memberId, ep2, 60)
                ));

        var response = seriesService.getSeriesContents(mediaId, 0, 10, memberId);

        // 반환된 DTO 목록에서 duration/position이 실제 DB 데이터와 일치하는지 확인
        assertThat(response.getDataList()).hasSize(2);
        SeriesContentsResponse first = response.getDataList().get(0);
        assertThat(first.getDuration()).isEqualTo(120);
        assertThat(first.getPositionSec()).isEqualTo(30);

        SeriesContentsResponse second = response.getDataList().get(1);
        assertThat(second.getDuration()).isEqualTo(200);
        assertThat(second.getPositionSec()).isEqualTo(60);

        // ContentsRepository에 Status/공개 상태 조건으로 호출되었는지 확인
        verify(contentsRepository).findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(eq(series.getId()), eq(Status.ACTIVE), eq(PublicStatus.PUBLIC), eq(MediaStatus.COMPLETED), any(Pageable.class));
    }

    @Test
    void getFirstEpisodeMediaId_throwsWhenNoEpisodesRegistered() {
        Long seriesId = 300L;
        Pageable limitOne = PageRequest.of(0, 1);
        when(contentsRepository.findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(seriesId, Status.ACTIVE, PublicStatus.PUBLIC, MediaStatus.COMPLETED, limitOne))
                .thenReturn(Page.empty(limitOne));

        // 1화가 없을 때 private helper가 EPISODE_NOT_REGISTERED 예외를 던지는지 검사
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(seriesService, "getFirstEpisodeMediaId", seriesId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EPISODE_NOT_REGISTERED);
    }

    @Test
    void getFirstEpisodeMediaId_returnsFirstEpisodeMediaId() {
        Long seriesId = 400L;
        Pageable limitOne = PageRequest.of(0, 1);
        Contents episode = createContents(401L, createSeries(seriesId + 1, 10L), 180);
        when(contentsRepository.findBySeriesIdAndStatusAndMedia_PublicStatusAndMedia_MediaStatusOrderByIdAsc(seriesId, Status.ACTIVE, PublicStatus.PUBLIC, MediaStatus.COMPLETED, limitOne))
                .thenReturn(new PageImpl<>(List.of(episode), limitOne, 1));

        // 1화가 존재하면 실제 mediaId를 반환하는지 확인
        Long mediaId = ReflectionTestUtils.invokeMethod(seriesService, "getFirstEpisodeMediaId", seriesId);
        assertThat(mediaId).isEqualTo(episode.getMedia().getId());
    }

    private static Series createSeries(Long seriesId, Long mediaId) {
        // 테스트용 Series/Media 엔티티를 간단히 만드는 헬퍼
        return Series.builder()
                .id(seriesId)
                .actors("crew")
                .media(Media.builder()
                        .id(mediaId)
                        .uploader(createMember(100L))
                        .title("series-title")
                        .description("series-desc")
                        .posterUrl("poster")
                        .thumbnailUrl("thumb")
                        .bookmarkCount(0L)
                        .likesCount(0L)
                        .mediaType(MediaType.SERIES)
                        .publicStatus(PublicStatus.PUBLIC)
                        .mediaStatus(MediaStatus.COMPLETED)
                        .build())
                .build();
    }

    // 컨텐츠/미디어 엔티티를 묶어서 재생 시간 단위 테스트에 쓰기 위한 헬퍼
    private static Contents createContents(Long mediaId, Series series, Integer duration) {
        return Contents.builder()
                .id(mediaId)
                .series(series)
                .media(Media.builder()
                        .id(mediaId)
                        .uploader(createMember(200L))
                        .title("episode-" + mediaId)
                        .description("desc")
                        .posterUrl("poster")
                        .thumbnailUrl("thumb")
                        .bookmarkCount(0L)
                        .likesCount(0L)
                        .mediaType(MediaType.CONTENTS)
                        .publicStatus(PublicStatus.PUBLIC)
                        .mediaStatus(MediaStatus.COMPLETED)
                        .build())
                .duration(duration)
                .actors("actor")
                .videoSize(1)
                .originUrl("origin")
                .masterPlaylistUrl("master")
                .build();
    }

    // memberId 별로 재생 기록 엔티티를 쉽게 만드는 헬퍼
    private static Playback createPlayback(Long memberId, Contents contents, Integer position) {
        return Playback.builder()
                .member(createMember(memberId))
                .contents(contents)
                .positionSec(position)
                .build();
    }

    // Member 엔티티 합성을 돕는 헬퍼
    private static Member createMember(Long id) {
        return Member.builder()
                .id(id)
                .email("member-" + id + "@ott")
                .nickname("member-" + id)
                .provider(Provider.KAKAO)
                .role(Role.MEMBER)
                .build();
    }
}
