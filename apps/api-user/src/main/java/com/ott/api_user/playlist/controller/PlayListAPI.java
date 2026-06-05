package com.ott.api_user.playlist.controller;

import com.ott.api_user.playlist.dto.response.PlaylistResponse;
import com.ott.api_user.playlist.dto.response.RecentWatchResponse;
import com.ott.api_user.playlist.dto.response.TagPlaylistResponse;
import com.ott.api_user.playlist.dto.response.TopTagPlaylistResponse;
import com.ott.common.web.exception.ErrorResponse;
import com.ott.common.web.response.SliceResponse;
import com.ott.common.web.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequestMapping("/playlists")
@SecurityRequirement(name = "BearerAuth") // 인증인가 확인
@Tag(name = "Playlist", description = "플레이리스트& 재생목록 API, excludeMediaId 는 재생목록 API 호출 시에만 포함시킵니다")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "조회 성공", 
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = SliceResponse.class))),
    @ApiResponse(responseCode = "401", description = "인증 실패", 
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "400", description = "요청 파라미터 오류", 
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
})
public interface PlayListAPI {
        @Operation(summary = "OO 님이 좋아하실만한 콘텐츠", description = "유저 취향을 합산하여 추천합니다. (홈 화면 셔플 지원)")
        @ApiResponse(responseCode = "0", description = "플레이리스트 DTO 응답 구조", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlaylistResponse.class)))
        @GetMapping("/recommend")
        ResponseEntity<SuccessResponse<SliceResponse<PlaylistResponse>>> getRecommendPlaylists(
                @Parameter(description = "현재 영상 ID") @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
                @PositiveOrZero @Parameter(description = "페이지 번호(0부터 시작)", example = "0") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );

        
        @Operation(summary = "선호 태그 순위별 리스트", description = "유저의 Top 3 태그 순위를 기반으로 제공합니다.")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "태그 순위별 조회 성공", 
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopTagPlaylistResponse.class))),
                @ApiResponse(responseCode = "0", description = "태그 순위별 DTO 응답 구조", 
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = TopTagPlaylistResponse.class)))
        })
        @GetMapping("/tags/top")
        ResponseEntity<SuccessResponse<TopTagPlaylistResponse>> getTopTagPlaylists(
                @Parameter(description = "현재 영상 ID") @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
                @PositiveOrZero @Max(value = 2, message = "인덱스는 2 이하여야 합니다.") @Parameter(description = "유저 취향 순위 (0, 1, 2)", required = true) @RequestParam(value = "index") Integer index,
                @PositiveOrZero @Parameter(description = "페이지 번호") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );

        

        @Operation(summary = "상세 페이지 - 특정 해시태그 리스트", description = "해당 태그의 영상만 제공합니다.")
        @ApiResponse(responseCode = "0", description = "플레이리스트 DTO 응답 구조", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlaylistResponse.class)))
        @ApiResponse(responseCode = "404", description = "해당 태그를 찾을 수 없음", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
        @GetMapping("/tags/{tagId}")
        ResponseEntity<SuccessResponse<SliceResponse<PlaylistResponse>>> getTagPlaylists(
                @Parameter(description = "태그 ID", required = true) @PathVariable(value = "tagId") Long tagId,
                @Parameter(description = "현재 영상 ID") @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
                @PositiveOrZero @Parameter(description = "페이지 번호") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );

        @Operation(summary = "인기 차트 (Trending)", description = "북마크가 많은 인기 순서대로 제공합니다.")
        @ApiResponse(responseCode = "0", description = "플레이리스트 DTO 응답 구조", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlaylistResponse.class)))
        @GetMapping("/trending")
        ResponseEntity<SuccessResponse<SliceResponse<PlaylistResponse>>> getTrendingPlaylists(
                @Parameter(description = "현재 영상 ID") @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
                @PositiveOrZero @Parameter(description = "페이지 번호") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );

        @Operation(summary = "시청 이력 (History)", description = "유저가 최근 시청한 영상 목록을 제공합니다.")
        @ApiResponse(responseCode = "0", description = "플레이리스트 DTO 응답 구조", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlaylistResponse.class)))
        @GetMapping("/history")
        ResponseEntity<SuccessResponse<SliceResponse<PlaylistResponse>>> getHistoryPlaylists(
                @Parameter(description = "현재 영상 ID") @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
                @PositiveOrZero @Parameter(description = "페이지 번호") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );

        @Operation(summary = "북마크 목록 (Bookmark)", description = "유저가 북마크한 영상 목록을 제공합니다.")
        @ApiResponse(responseCode = "0", description = "플레이리스트 DTO 응답 구조", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlaylistResponse.class)))
        @GetMapping("/bookmarks")
        ResponseEntity<SuccessResponse<SliceResponse<PlaylistResponse>>> getBookmarkPlaylists(
                @Parameter(description = "현재 영상 ID") @RequestParam(value = "excludeMediaId", required = false) Long excludeMediaId,
                @PositiveOrZero @Parameter(description = "페이지 번호") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );

        @Operation(summary = "검색 상세 페이지 재생목록", description = "검색 결과에서 진입 시 종합 추천 리스트로 대체하여 제공합니다.")
        @ApiResponse(responseCode = "0", description = "플레이리스트 DTO 응답 구조", 
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlaylistResponse.class)))
        @GetMapping("/search")
        ResponseEntity<SuccessResponse<SliceResponse<PlaylistResponse>>> getSearchPlaylists(
                @Parameter(description = "현재 영상 ID", required = true) @RequestParam(value = "excludeMediaId") Long excludeMediaId,
                @PositiveOrZero @Parameter(description = "페이지 번호") @RequestParam(value = "page", defaultValue = "0") Integer page,
                @Positive @Parameter(description = "페이지 크기") @RequestParam(value = "size", defaultValue = "20") Integer size,
                @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
        );


                // -------------------------------------------------------
        // 태그별 추천 콘텐츠 목록 조회
        // -------------------------------------------------------
        //     @Operation(summary = "[마이페이지] 태그별 추천 콘텐츠 리스트 조회", description = "해당 태그에 속하는 콘텐츠를 최대 20개 반환"
        //     )
        //     @ApiResponses({
        //             @ApiResponse(
        //                     responseCode = "200", description = "조회 성공",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = TagPlaylistResponse.class)
        //                     )
        //             ),
        //             @ApiResponse(
        //                     responseCode = "400", description = "요청 파라미터 오류",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)
        //                     )
        //             ),
        //             @ApiResponse(
        //                     responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)
        //                     )
        //             ),
        //             @ApiResponse(
        //                     responseCode = "404", description = "회원 또는 태그를 찾을 수 없음",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)
        //                     )
        //             )
        //     })
        //     @GetMapping("/me/{tagId}")
        //     ResponseEntity<SuccessResponse<List<TagPlaylistResponse>>> getRecommendContentsByTag(
        //             @AuthenticationPrincipal Long memberId,
        //             @Positive @PathVariable Long tagId
        //     );


        // -------------------------------------------------------
        // 전체 시청이력 플레이리스트 페이징 조회
        // -------------------------------------------------------
        //     @Operation(summary = "[마이페이지] 과거 시청 이력 리스트 조회", description = "전체 시청이력을 최신순으로 10개씩 페이징 조회합니다. 이어보기 시점 포함.")
        //     @ApiResponses({
        //             @ApiResponse(
        //                     responseCode = "200", description = "조회 성공",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = SliceResponse.class))),
        //             @ApiResponse(
        //                     responseCode = "401", description = "인증 실패",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        //             @ApiResponse(
        //                     responseCode = "404", description = "회원을 찾을 수 없음",
        //                     content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
        //     })
        //     @GetMapping("/me/history")
        //     ResponseEntity<SuccessResponse<SliceResponse<RecentWatchResponse>>> getWatchHistoryPlaylist(
        //             @AuthenticationPrincipal Long memberId,
        //             @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        //             @PositiveOrZero @RequestParam(defaultValue = "0") Integer page
        //     );
}
