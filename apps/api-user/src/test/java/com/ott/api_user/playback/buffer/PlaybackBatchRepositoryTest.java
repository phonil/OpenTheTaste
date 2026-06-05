package com.ott.api_user.playback.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@Import(PlaybackBatchRepository.class)
@Sql(statements = {
    "CREATE TABLE IF NOT EXISTS playback ("
        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
        + "member_id BIGINT NOT NULL,"
        + "contents_id BIGINT NOT NULL,"
        + "position_sec INT NOT NULL DEFAULT 0,"
        + "event_time DATETIME(3) NOT NULL DEFAULT NOW(),"
        + "created_date DATETIME NOT NULL,"
        + "modified_date DATETIME NOT NULL,"
        + "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',"
        + "UNIQUE KEY uk_member_contents (member_id, contents_id))",
    "INSERT INTO playback (member_id, contents_id, position_sec, event_time, created_date, modified_date, status) VALUES (1, 10, 0, '2026-01-01 00:00:00', NOW(), NOW(), 'ACTIVE')",
    "INSERT INTO playback (member_id, contents_id, position_sec, event_time, created_date, modified_date, status) VALUES (2, 20, 0, '2026-01-01 00:00:00', NOW(), NOW(), 'ACTIVE')",
    "INSERT INTO playback (member_id, contents_id, position_sec, event_time, created_date, modified_date, status) VALUES (3, 30, 0, '2026-01-01 00:00:00', NOW(), NOW(), 'ACTIVE')"
})
class PlaybackBatchRepositoryTest {

    @Autowired
    private PlaybackBatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void batchUpdate_updatesMultipleRows() {
        List<PlaybackCommand> commands = List.of(
            new PlaybackCommand(1L, 10L, 120, Instant.now()),
            new PlaybackCommand(2L, 20L, 350, Instant.now()),
            new PlaybackCommand(3L, 30L, 500, Instant.now())
        );

        batchRepository.batchUpdate(commands);

        assertThat(getPositionSec(1L, 10L)).isEqualTo(120);
        assertThat(getPositionSec(2L, 20L)).isEqualTo(350);
        assertThat(getPositionSec(3L, 30L)).isEqualTo(500);
    }

    @Test
    void batchUpdate_ignoresNonExistentRow() {
        List<PlaybackCommand> commands = List.of(
            new PlaybackCommand(999L, 999L, 100, Instant.now())
        );

        assertThatNoException().isThrownBy(() -> batchRepository.batchUpdate(commands));
    }

    @Test
    void batchUpdate_emptyCollection() {
        assertThatNoException().isThrownBy(() -> batchRepository.batchUpdate(List.of()));
    }

    @Test
    void batchUpdate_ignoresOlderEventTime() {
        // 먼저 최신 event_time으로 갱신
        Instant newer = Instant.parse("2026-06-01T00:00:00Z");
        batchRepository.batchUpdate(List.of(
            new PlaybackCommand(1L, 10L, 200, newer)
        ));
        assertThat(getPositionSec(1L, 10L)).isEqualTo(200);

        // 과거 event_time으로 갱신 시도 → 무시되어야 함
        Instant older = Instant.parse("2026-05-01T00:00:00Z");
        batchRepository.batchUpdate(List.of(
            new PlaybackCommand(1L, 10L, 100, older)
        ));
        assertThat(getPositionSec(1L, 10L)).isEqualTo(200);
    }

    private int getPositionSec(long memberId, long contentsId) {
        return jdbcTemplate.queryForObject(
            "SELECT position_sec FROM playback WHERE member_id = ? AND contents_id = ?",
            Integer.class,
            memberId, contentsId
        );
    }
}
