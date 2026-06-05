package com.ott.api_user.playlist.dto.response;

import com.ott.common.web.response.SliceResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "홈 화면 태그별 섹션 응답 DTO")
public class TopTagPlaylistResponse {

    // 유빈님이 좋아하는 #로맨스 영화
    // 유빈님이 좋아하는 #로맨스 드라마
    @Schema(description = "카테고리 정보")
    private CategoryInfo category;

    @Schema(description = "태그 정보")
    private TagInfo tag;

    @Schema(description = "해당 태그의 미디어 목록")
    private SliceResponse<PlaylistResponse> medias;

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class TagInfo {
        private Long id;
        private String name;
    }
}
