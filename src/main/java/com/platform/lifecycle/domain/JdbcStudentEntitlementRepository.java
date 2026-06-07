package com.platform.lifecycle.domain;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed {@link StudentEntitlementRepository}. The upsert is a single {@code INSERT ... ON
 * CONFLICT (user_id) DO UPDATE}, so applying the same {@code EntitlementChanged} twice leaves exactly
 * one row with the latest features. The feature array is bound as {@code jsonb}.
 */
@Repository
public class JdbcStudentEntitlementRepository implements StudentEntitlementRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStudentEntitlementRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(UUID userId, String allowedFeaturesJson) {
        jdbc.update("""
                INSERT INTO student_entitlements (user_id, allowed_features, updated_at)
                VALUES (:userId, CAST(:features AS jsonb), now())
                ON CONFLICT (user_id) DO UPDATE
                    SET allowed_features = EXCLUDED.allowed_features, updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("features", allowedFeaturesJson));
    }
}
