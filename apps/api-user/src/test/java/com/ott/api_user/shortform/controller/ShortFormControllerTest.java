package com.ott.api_user.shortform.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.ott.api_user.shortform.dto.response.ShortFormFeedResponse;
import com.ott.api_user.shortform.service.ClickEventService;
import com.ott.api_user.shortform.service.ShortFormFeedService;
import com.ott.common.web.response.PageInfo;
import com.ott.common.web.response.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ShortFormControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ShortFormFeedService shortFormFeedService;

    @Mock
    private ClickEventService clickEventService;

    @InjectMocks
    private ShortFormController shortFormController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shortFormController)
                .setCustomArgumentResolvers(
                        new HandlerMethodArgumentResolver() { // memberId(Principal) 자동 해석기
                            @Override
                            public boolean supportsParameter(MethodParameter parameter) {
                                return parameter.getParameterType().equals(Long.class);
                            }
                            @Override
                            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                                return Long.valueOf(webRequest.getUserPrincipal().getName());
                            }
                        }
                )
                .build();
    }

    @Test
    void getShortFormFeed_returnsSuccessResponseWithPageInfo() throws Exception {
        Long memberId = 5L;
        ShortFormFeedResponse payload = ShortFormFeedResponse.builder()
                .shortFormId(111L)
                .title("title")
                .editorName("editor")
                .uploadDate(null)
                .isBookmarked(false)
                .isLiked(true)
                .shortMasterPlaylistUrl("url")
                .originMediaId(222L)
                .mediaType(null)
                .build();

        PageInfo pageInfo = PageInfo.builder().currentPage(1).pageSize(3).build();
        PageResponse<ShortFormFeedResponse> response = PageResponse.toPageResponse(pageInfo, List.of(payload));

        when(shortFormFeedService.getShortFormFeed(memberId, 1, 3)).thenReturn(response);

        mockMvc.perform(get("/short-forms")
                        .principal(new UsernamePasswordAuthenticationToken(memberId, "x"))
                        .param("page", "1")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageInfo.currentPage").value(1))
                .andExpect(jsonPath("$.data.dataList[0].shortFormId").value(111L));

        verify(shortFormFeedService).getShortFormFeed(memberId, 1, 3);
    }

    @Test
    void recordShortFormView_callsShortClick() throws Exception {
        Long memberId = 7L;

        mockMvc.perform(post("/short-forms/events")
                        .principal(new UsernamePasswordAuthenticationToken(memberId, "x"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":7}"))
                .andExpect(status().isNoContent());

        verify(clickEventService).saveClickEvent(eq(memberId), eq(7L), eq(com.ott.domain.click_event.domain.ClickType.SHORT_CLICK));
    }

    @Test
    void recordCtaClick_callsCtaClickType() throws Exception {
        Long memberId = 8L;

        mockMvc.perform(post("/short-forms/cta")
                        .principal(new UsernamePasswordAuthenticationToken(memberId, "x"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":13}"))
                .andExpect(status().isNoContent());

        verify(clickEventService).saveClickEvent(memberId, 13L, com.ott.domain.click_event.domain.ClickType.CTA_CLICK);
    }
}
