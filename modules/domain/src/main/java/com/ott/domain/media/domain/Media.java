package com.ott.domain.media.domain;

import com.ott.domain.common.BaseEntity;
import com.ott.domain.common.MediaType;
import com.ott.domain.common.PublicStatus;
import com.ott.domain.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "media")
public class Media extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private Member uploader;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "poster_url", nullable = false, columnDefinition = "TEXT")
    private String posterUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "bookmark_count", nullable = false)
    private Long bookmarkCount;

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_status", nullable = false)
    private PublicStatus publicStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_status", nullable = false)
    private MediaStatus mediaStatus;

    @Column(name = "is_standalone", nullable = false)
    private Boolean isStandalone;

    public void updateMediaStatus(MediaStatus mediaStatus) {
        this.mediaStatus = mediaStatus;
    }

    public void updateImageKeys(String posterUrl, String thumbnailUrl) {
        this.posterUrl = posterUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void updateMetadata(String title, String description, PublicStatus publicStatus) {
        this.title = title;
        this.description = description;
        this.publicStatus = publicStatus;
    }

    // 북마크 증가 메소드
    public void increaseBookmarkCount() {
        this.bookmarkCount++;
    }

    // 북마크 감소 메소드
    public void decreaseBookmarkCount() {
        if (this.bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }

    public void increaseLikesCount() {
        this.likesCount++;
    }

    public void decreaseLikesCount() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
