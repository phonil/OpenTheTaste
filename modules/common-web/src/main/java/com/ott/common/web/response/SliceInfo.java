package com.ott.common.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class SliceInfo {

    @Schema(type = "Integer", example = "0", description = "현재 페이지 (0부터 시작)")
    private Integer currentPage;

    @Schema(type = "Integer", example = "20", description = "한 페이지의 사이즈")
    private Integer pageSize;

    @Schema(type = "Boolean", example = "true", description = "다음 페이지 존재 여부")
    private Boolean hasNext;

    @Builder
    public SliceInfo(Integer currentPage, Integer pageSize, Boolean hasNext) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.hasNext = hasNext;
    }
}
