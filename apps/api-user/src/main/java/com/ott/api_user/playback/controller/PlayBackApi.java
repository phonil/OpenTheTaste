package com.ott.api_user.playback.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;

import com.ott.api_user.playback.dto.request.PlaybackInitRequest;
import com.ott.api_user.playback.dto.request.PlaybackUpdateRequest;
import com.ott.common.web.exception.ErrorResponse;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@Tag(name = "PlayBack", description = "이어보기 갱신 및 조회 API")
public interface PlayBackApi {

    @Operation(summary = "이어보기 위치 초기화", description = "재생 시작 시 이어보기 row를 보장합니다. 최초 재생 지점은 0초로 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "이어보기 위치 초기화 성공, 응답 본문 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (mediaId 누락 등)", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) }),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 미디어 ID", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) })
    })
    ResponseEntity<Void> initPlayback(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PlaybackInitRequest request
    );

    @Operation(summary = "이어보기 위치 갱신", description = "영상 재생 중 이어보기 위치(positionSec)를 갱신합니다.")
    @ApiResponses(value ={
            @ApiResponse(responseCode = "204", description = "이어보기 위치 갱신 성공, 응답 본문 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (mediaId 누락 등)", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) }),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 미디어 ID", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)) })
    })
        ResponseEntity<Void> updatePlayBack(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId, 
            @Valid @RequestBody PlaybackUpdateRequest request 
    );
} 
