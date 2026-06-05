package com.ott.domain.contents.repository;

import com.ott.domain.contents.domain.Contents;
import com.ott.domain.series.domain.Series;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContentsRepositoryCustom {

    Optional<Contents> findWithMediaById(Long contentsId);

    Optional<Contents> findWithMediaAndUploaderByMediaId(Long mediaId);

    List<Contents> findAllByMediaIdIn(List<Long> mediaIdList);

    List<Contents> findLastEpisodeBySeriesMediaIds(List<Long> seriesMediaIdList);

    // [4-2] 시리즈 ID 목록 → 시리즈별 1화 Media ID 일괄 조회 (N+1 해결)
    Map<Long, Long> findFirstEpisodeMediaIdsBySeriesMediaIds(List<Long> seriesMediaIdList);
}
