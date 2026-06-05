package com.ott.domain.playback.domain;

import com.ott.domain.common.BaseEntity;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.member.domain.Member;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Builder
@Getter
@Table(
        name = "playback", 
        uniqueConstraints  = {
            @UniqueConstraint(
                name = "uk_playback_member_contents",
                columnNames = {"member_id", "contents_id"}
          )
       }
)
public class Playback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contents_id", nullable = false)
    private Contents contents;

    @Column(name = "position_sec", nullable = false)
    private Integer positionSec;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;
}
