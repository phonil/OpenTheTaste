package com.ott.api_user.auth.service;

import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.member.repository.MemberRepository;
import com.ott.domain.member_radar_preference.domain.MemberRadarPreference;
import com.ott.domain.member_radar_preference.repository.MemberRadarPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TestAuthService {

    private final MemberRepository memberRepository;
    private final MemberRadarPreferenceRepository memberRadarPreferenceRepository;

    public Member findOrCreateTestMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseGet(() -> {
                    String nickname = email.split("@")[0];
                    Member newMember = memberRepository.save(
                            Member.builder()
                                    .email(email)
                                    .nickname(nickname)
                                    .provider(Provider.LOCAL)
                                    .providerId("TEST_" + UUID.randomUUID().toString().substring(0, 8))
                                    .role(Role.MEMBER)
                                    .onboardingCompleted(true)
                                    .build()
                    );
                    memberRadarPreferenceRepository.save(
                            MemberRadarPreference.createDefault(newMember));
                    return newMember;
                });
    }
}
