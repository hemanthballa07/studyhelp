package com.platform.expertportal.domain;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC-backed {@link ExpertSubjectRepository}. Registration is idempotent via {@code ON CONFLICT}. */
@Repository
public class JdbcExpertSubjectRepository implements ExpertSubjectRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcExpertSubjectRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void register(UUID expertId, String subject) {
        jdbc.update("""
                INSERT INTO expert_subjects (expert_id, subject)
                VALUES (:expertId, :subject)
                ON CONFLICT (expert_id, subject) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("expertId", expertId)
                        .addValue("subject", subject));
    }

    @Override
    public boolean handles(UUID expertId, String subject) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM expert_subjects WHERE expert_id = :expertId AND subject = :subject)
                """,
                new MapSqlParameterSource()
                        .addValue("expertId", expertId)
                        .addValue("subject", subject),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
