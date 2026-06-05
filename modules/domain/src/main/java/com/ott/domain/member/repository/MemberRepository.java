package com.ott.domain.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ott.domain.common.Status;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    // 기존 회원인지 신규 회원인지 DB 조회
    Optional<Member> findByProviderAndProviderId(Provider provider, String providerId);

    // 관리자&에디터용 조회
    Optional<Member> findByEmailAndProvider(String email, Provider provider);

    // Active한 유저 조회
    Optional<Member> findByIdAndStatus(Long memberId, Status status);

    // 해당 쿼리는 조회가 아닌 jpql를 사용한 수정, 삭제, 삽입 쿼리임을 명시
    @Modifying
    @Query("""
      UPDATE Member m
      SET m.refreshToken = null,
          m.providerId = null,
          m.onboardingCompleted = false,
          m.status = 'DELETE'
      WHERE m.id = :memberId
      """)
    void softDeleteByMemberId(@Param("memberId") Long memberId);

    Optional<Member> findByEmail(String email);
}
