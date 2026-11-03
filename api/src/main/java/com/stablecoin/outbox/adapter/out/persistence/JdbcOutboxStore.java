package com.stablecoin.outbox.adapter.out.persistence;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.outbox.application.port.out.OutboxStorePort;
import com.stablecoin.outbox.domain.ClaimedOutboxMessage;

/**
 * 발행기용 outbox 쿼리. 각 쿼리는 하나의 SQL 문이라 자동 커밋으로 바로 반영된다.
 */
@Component
class JdbcOutboxStore implements OutboxStorePort {

	/** last_error 에 남기는 최대 길이 */
	private static final int MAX_ERROR_LENGTH = 1000;

	/**
	 * 발행할 차례인 메시지를 고르고(FOR UPDATE SKIP LOCKED로 다른 워커와 겹치지 않게) 같은 문장에서 잠금 표시를 한다.
	 * locked_at 이 lease 보다 오래됐으면 잡았던 워커가 죽은 것으로 보고 다시 잡는다.
	 */
	private static final String CLAIM = """
			WITH candidates AS (
			    SELECT id
			    FROM outbox_messages
			    WHERE status = 'PENDING'
			      AND available_at <= now()
			      AND (locked_at IS NULL OR locked_at < now() - make_interval(secs => :leaseSeconds))
			    ORDER BY created_at, id
			    LIMIT :limit
			    FOR UPDATE SKIP LOCKED
			)
			UPDATE outbox_messages AS outbox
			SET locked_at = now(), locked_by = :workerId, attempts = outbox.attempts + 1
			FROM candidates
			WHERE outbox.id = candidates.id
			RETURNING outbox.id, outbox.aggregate_type, outbox.aggregate_id, outbox.type,
			          outbox.payload::text AS payload, outbox.attempts, outbox.created_at
			""";

	/** 이 워커가 아직 잡고 있을 때만 바꾼다 (임대가 끝나 다른 워커가 가져갔으면 0행) */
	private static final String MARK_PUBLISHED = """
			UPDATE outbox_messages
			SET status = 'PUBLISHED', published_at = now(), locked_at = NULL, locked_by = NULL, last_error = NULL
			WHERE id = :id AND locked_by = :workerId AND status = 'PENDING'
			""";

	private static final String MARK_RETRY = """
			UPDATE outbox_messages
			SET available_at = now() + make_interval(secs => :retryAfterSeconds),
			    locked_at = NULL, locked_by = NULL, last_error = :error
			WHERE id = :id AND locked_by = :workerId AND status = 'PENDING'
			""";

	private static final String MARK_GIVEN_UP = """
			UPDATE outbox_messages
			SET status = 'FAILED', locked_at = NULL, locked_by = NULL, last_error = :error
			WHERE id = :id AND locked_by = :workerId AND status = 'PENDING'
			""";

	private final NamedParameterJdbcTemplate jdbc;

	JdbcOutboxStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ClaimedOutboxMessage> claimBatch(String workerId, int limit, Duration lease) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("workerId", workerId)
			.addValue("limit", limit)
			.addValue("leaseSeconds", seconds(lease));
		// RETURNING 의 순서는 보장되지 않으므로 생성 순서로 다시 정렬한다
		return jdbc.queryForList(CLAIM, params)
			.stream()
			.sorted(Comparator.comparing((Map<String, Object> row) -> row.get("created_at").toString())
				.thenComparing(row -> row.get("id").toString()))
			.map(row -> new ClaimedOutboxMessage((UUID) row.get("id"), (String) row.get("aggregate_type"),
					(UUID) row.get("aggregate_id"), (String) row.get("type"), (String) row.get("payload"),
					((Number) row.get("attempts")).intValue()))
			.toList();
	}

	@Override
	public boolean markPublished(UUID messageId, String workerId) {
		return jdbc.update(MARK_PUBLISHED,
				new MapSqlParameterSource().addValue("id", messageId).addValue("workerId", workerId)) == 1;
	}

	@Override
	public void markFailed(UUID messageId, String workerId, String error, Duration retryAfter, boolean giveUp) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", messageId)
			.addValue("workerId", workerId)
			.addValue("error", truncate(error));
		if (giveUp) {
			jdbc.update(MARK_GIVEN_UP, params);
		}
		else {
			jdbc.update(MARK_RETRY, params.addValue("retryAfterSeconds", seconds(retryAfter)));
		}
	}

	private static double seconds(Duration duration) {
		return duration.toMillis() / 1000.0;
	}

	private static String truncate(String error) {
		if (error == null) {
			return null;
		}
		return (error.length() <= MAX_ERROR_LENGTH) ? error : error.substring(0, MAX_ERROR_LENGTH);
	}

}
