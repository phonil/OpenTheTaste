package com.ott.api_user.playback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "이어보기 초기화 요청 DTO")
public class PlaybackInitRequest {

    @NotNull(message = "미디어 ID는 필수입니다.")
    @Schema(type = "Long", description = "미디어 ID", example = "101")
    private Long mediaId;
}
