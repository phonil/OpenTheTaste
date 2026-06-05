package com.ott.api_user.playlist.dto.response;

import com.ott.domain.common.MediaType;
import com.ott.domain.media.domain.Media;
import lombok.AccessLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "플레이리스트 조회 응답 DTO")
public class PlaylistResponse {

    @Schema(description = "미디어 고유 ID", example = "100")
    private Long mediaId;

    @Schema(description = "컨텐츠 제목", example = "비밀의 숲")
    private String title;

    @Schema(description = "포스터 이미지 URL", example = "https://s3.../poster.jpg")
    private String posterUrl;

    @Schema(description = "가로형 썸네일 이미지 URL", example = "https://cdn.ott.com/thumbnails/101.jpg")
    private String thumbnailUrl;
    
    @Schema(description = "미디어 타입 (UI 분기 처리 및 라우팅용)", example = "SERIES")
    private MediaType mediaType;

    
    @Schema(description= "재생 시간 (초)", example = "3600")
    private Integer duration;

    @Schema(description = "기존 이어보기 지점(없으면 0)", example = "150")
    private Integer positionSec;

    
    public static PlaylistResponse from(Media media, Integer duration, Integer positionSec) {
        return PlaylistResponse.builder()
                .mediaId(media.getId())
                .title(media.getTitle())
                .posterUrl(media.getPosterUrl())
                .thumbnailUrl(media.getThumbnailUrl())
                .mediaType(media.getMediaType())
                .duration(duration != null ? duration : 0) // null 방어
                .positionSec(positionSec != null ? positionSec : 0)
                .build();
    }
}
