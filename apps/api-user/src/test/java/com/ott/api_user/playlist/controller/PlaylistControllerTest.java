package com.ott.api_user.playlist.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.ott.api_user.common.ContentSource;
import com.ott.api_user.playlist.dto.request.PlaylistCondition;
import com.ott.api_user.playlist.dto.response.PlaylistResponse;
import com.ott.api_user.playlist.dto.response.TopTagPlaylistResponse;
import com.ott.api_user.playlist.service.PlaylistStrategyService;
import com.ott.common.web.response.SliceInfo;
import com.ott.common.web.response.SliceResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PlaylistStrategyService playlistStrategyService;

    @InjectMocks
    private PlaylistController playlistController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(playlistController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(), // 1. Pageable 파라미터 자동 해석기
                        new HandlerMethodArgumentResolver() { // 2. memberId(Principal) 자동 해석기
                            @Override
                            public boolean supportsParameter(MethodParameter parameter) {
                                return parameter.getParameterType().equals(Long.class);
                            }
                            @Override
                            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                                // .principal() 로 넣은 값을 Long(memberId)으로 변환해서 주입합니다.
                                return Long.valueOf(webRequest.getUserPrincipal().getName());
                            }
                        }
                )
                .build();
    }

    @Test
    void getRecommendPlaylists_invokesServiceAndWrapsResponse() throws Exception {
        Long memberId = 99L;
        PlaylistResponse playlist = PlaylistResponse.builder()
                .mediaId(77L)
                .title("title")
                .posterUrl("poster")
                .thumbnailUrl("thumb")
                .mediaType(null)
                .duration(120)
                .positionSec(10)
                .build();

        SliceResponse<PlaylistResponse> sliceResponse = SliceResponse.toSliceResponse(
                SliceInfo.builder().currentPage(2).pageSize(4).hasNext(false).build(),
                List.of(playlist)
        );

        when(playlistStrategyService.getPlaylists(any(PlaylistCondition.class), any(Pageable.class)))
                .thenReturn(sliceResponse);

        mockMvc.perform(get("/playlists/recommend")
                        .principal(new UsernamePasswordAuthenticationToken(memberId, "x"))
                        .param("page", "2")
                        .param("size", "4")
                        .param("excludeMediaId", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sliceInfo.pageSize").value(4));

        ArgumentCaptor<PlaylistCondition> conditionCaptor = ArgumentCaptor.forClass(PlaylistCondition.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(playlistStrategyService).getPlaylists(conditionCaptor.capture(), pageableCaptor.capture());
        PlaylistCondition capturedCondition = conditionCaptor.getValue();
        assert capturedCondition.getContentSource() == ContentSource.RECOMMEND;
        assert capturedCondition.getExcludeMediaId().equals(123L);
        Pageable capturedPageable = pageableCaptor.getValue();
        assert capturedPageable.getPageNumber() == 2;
        assert capturedPageable.getPageSize() == 4;
        PlaylistCondition captured = conditionCaptor.getValue();
        assert captured.getContentSource() == ContentSource.RECOMMEND;
        assert captured.getExcludeMediaId().equals(123L);
    }

    @Test
    void getTopTagPlaylists_returnsTopTagResponse() throws Exception {
        Long memberId = 88L;

        SliceResponse<PlaylistResponse> playlists = SliceResponse.toSliceResponse(
                SliceInfo.builder().currentPage(0).pageSize(1).hasNext(false).build(),
                List.of(PlaylistResponse.builder().mediaId(1L).title("movie").posterUrl("p").thumbnailUrl("t").mediaType(null).duration(60).positionSec(0).build())
        );

        TopTagPlaylistResponse payload = TopTagPlaylistResponse.builder()
                .tag(TopTagPlaylistResponse.TagInfo.builder().id(3L).name("tag").build())
                .category(TopTagPlaylistResponse.CategoryInfo.builder().id(4L).name("cat").build())
                .medias(playlists)
                .build();

        when(playlistStrategyService.getTopTagPlaylistWithMetadata(any(PlaylistCondition.class), any(Pageable.class)))
                .thenReturn(payload);

        mockMvc.perform(get("/playlists/tags/top")
                        .principal(new UsernamePasswordAuthenticationToken(memberId, "x"))
                        .param("index", "0")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag.id").value(3L))
                .andExpect(jsonPath("$.data.category.name").value("cat"))
                .andExpect(jsonPath("$.data.medias.dataList[0].mediaId").value(1L));

        ArgumentCaptor<PlaylistCondition> conditionCaptor = ArgumentCaptor.forClass(PlaylistCondition.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(playlistStrategyService).getTopTagPlaylistWithMetadata(conditionCaptor.capture(), pageableCaptor.capture());
        PlaylistCondition captured = conditionCaptor.getValue();
        assert captured.getContentSource() == ContentSource.TAG;
        assert captured.getIndex().equals(0);
    }
}
