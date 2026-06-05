package com.ott.api_user.config;

import com.ott.api_user.auth.oauth2.CustomOAuth2UserService;
import com.ott.api_user.auth.oauth2.handler.OAuth2FailureHandler;
import com.ott.api_user.auth.oauth2.handler.OAuth2SuccessHandler;
import com.ott.common.security.filter.JwtAuthenticationFilter;
import com.ott.common.security.handler.JwtAccessDeniedHandler;
import com.ott.common.security.handler.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // api-user 전용 OAuth2
    private final CustomOAuth2UserService CustomOAuth2UserService; // 카카오에서 받은 사용자 프로필 조회 후 DB에 적재
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Value("${app.frontend-url:http://localhost:8080}")
    private String frontedUrl;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable) // csrf 비활성화, Authorization 헤더로 보냄
                .formLogin(AbstractHttpConfigurer::disable) // 카카오 OAtuh2 + JWT기반이라 기본 로그인 폼 안씀
                .httpBasic(AbstractHttpConfigurer::disable) // 카카오 OAtuh2 + JWT기반이라 Basic 인증 안씀
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // JWT 기반 인증이라 세션 유지 x

                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 401
                        .accessDeniedHandler(jwtAccessDeniedHandler)) // 403

                .authorizeHttpRequests(auth -> auth
                        // 인증 불필요
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/prometheus/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/auth/reissue",
                                "/auth/test-login",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**"
                        ).permitAll()

                        /*
                        역할이 MEMBER인 유저만 그 외 EndPoint 접근 가능하도록 설정
                         */
                        .anyRequest().hasRole("MEMBER")
                )

                // OAuth2 카카오 로그인
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(CustomOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )

                // Spring Security보다 먼저 실행
                // 쿠키에서 AccessToken을 꺼내와서 검증 이후 SecurityContext에 인증 정보 박제
                // 해당 과정에서 memberId, ROLE을 context에 넣어줌
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // allowedOrigins -> 허용할 도메인 내역
        // allowCredentials -> 브라우저가 요청에 인증정보를 포함하는 것을 허용하겠냐
        // credentials가 true일 경우 Allow-origin의 경우 구체적인 경로를 명시해야됨

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://www.openthetaste.cloud"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*")); // 모든 헤더 다 받는데 우리 서비스에서는 안씀
        config.setAllowCredentials(true); // 쿠키 요청을 포함

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 위 설정을 모든 경로에 적용
        return source;
    }

}
