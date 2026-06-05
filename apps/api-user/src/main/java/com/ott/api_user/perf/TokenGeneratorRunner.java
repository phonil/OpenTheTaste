package com.ott.api_user.perf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ott.common.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * k6 부하 테스트용 JWT 토큰 일괄 생성기.
 *
 * perf 프로파일에서만 동작하며, 시드 사용자 1,000명의 accessToken을
 * 생성하여 k6/data/tokens.json에 출력한 뒤 앱을 종료한다.
 *
 * 실행:
 *   SPRING_PROFILES_ACTIVE=perf ./gradlew :apps:api-user:bootRun
 *
 * 출력:
 *   k6/data/tokens.json — [{ "memberId": 1, "token": "eyJ..." }, ...]
 */
@Slf4j
@Component
@Profile("perf")
@RequiredArgsConstructor
public class TokenGeneratorRunner implements CommandLineRunner {

    private final JwtTokenProvider jwtTokenProvider;

    private static final int TOKEN_COUNT = 1000;
    private static final String OUTPUT_PATH = "k6/data/tokens.json";

    @Override
    public void run(String... args) throws Exception {
        log.info("[perf] 토큰 생성 시작 — {}명", TOKEN_COUNT);

        List<Map<String, Object>> tokenList = new ArrayList<>();

        for (long memberId = 1; memberId <= TOKEN_COUNT; memberId++) {
            String token = jwtTokenProvider.createAccessToken(
                    memberId, List.of("ROLE_MEMBER")
            );
            tokenList.add(Map.of(
                    "memberId", memberId,
                    "token", token
            ));
        }

        File outputFile = new File(OUTPUT_PATH);
        outputFile.getParentFile().mkdirs();

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputFile, tokenList);

        log.info("[perf] 토큰 생성 완료 — {} → {}", TOKEN_COUNT, OUTPUT_PATH);
        System.exit(0);
    }
}
