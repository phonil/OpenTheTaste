package com.ott.common.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SliceResponse<T> {

    @Schema(description = "슬라이스 페이징 정보")
    private SliceInfo sliceInfo;

    @Schema(description = "데이터 리스트")
    private List<T> dataList;

    public static <T> SliceResponse<T> toSliceResponse(SliceInfo sliceInfo, List<T> dataList) {
        return new SliceResponse<>(sliceInfo, dataList);
    }
}
