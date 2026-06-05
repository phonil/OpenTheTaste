package com.ott.api_user.auth.controller;

import com.ott.api_user.auth.cdn.CloudFrontSignedCookieService;
import com.ott.api_user.auth.service.TestAuthService;
import com.ott.common.security.jwt.JwtTokenProvider;
import com.ott.common.security.util.CookieUtil;
import com.ott.domain.member.domain.Member;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

//@Profile({"!prod"}) // 운영 환경에서는 비활성화
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TestAuthController {

    private final TestAuthService testAuthService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;
    private final CloudFrontSignedCookieService cloudFrontSignedCookieService;

    @Value("${jwt.access-token-expiry}")
    private int accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private int refreshTokenExpiry;

    @Value("${app.auth.cookie.access-name:userAccessToken}")
    private String accessCookieName;

    @Value("${app.auth.cookie.refresh-name:userRefreshToken}")
    private String refreshCookieName;

    @PostMapping("/test-login")
    public ResponseEntity<Void> testLogin(
            @RequestParam(defaultValue = "testuser1@test.com") String email,
            HttpServletResponse response) {

        Member member = testAuthService.findOrCreateTestMember(email);

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), List.of(member.getRole().getKey()));
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), List.of(member.getRole().getKey()));

        cookieUtil.addCookie(response, accessCookieName, accessToken, accessTokenExpiry);
        cookieUtil.addCookie(response, refreshCookieName, refreshToken, refreshTokenExpiry);
//        cloudFrontSignedCookieService.addSignedCookies(response);

        return ResponseEntity.noContent().build();
    }
}
