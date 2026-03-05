package com.template.worker.jobs.reconciliation.reader;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationFamilyReader implements ItemReader<Long>, StepExecutionListener {

    private static final String READ_ACTIVE_FAMILY_SQL =
            """
            SELECT id
            FROM family
            WHERE deleted_at IS NULL
              AND id > ?
            ORDER BY id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DbRedisReconciliationProperties properties;

    private Iterator<Long> currentBatch = Collections.emptyIterator();
    private long lastFamilyId = 0L;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // Step 재실행 시 상태 누수를 막기 위해 커서를 초기화함
        currentBatch = Collections.emptyIterator();
        lastFamilyId = 0L;
    }

    @Override
    public Long read() {
        while (!currentBatch.hasNext()) {
            // PK 커서 기반으로 배치 단위 조회를 수행함
            List<Long> familyIds =
                    jdbcTemplate.query(
                            READ_ACTIVE_FAMILY_SQL,
                            (resultSet, rowNum) -> resultSet.getLong("id"),
                            lastFamilyId,
                            properties.getDbFetchSize());

            // 더 이상 데이터가 없으면 null을 반환해 Chunk 처리를 종료함
            if (familyIds.isEmpty()) {
                return null;
            }

            lastFamilyId = familyIds.get(familyIds.size() - 1);
            currentBatch = familyIds.iterator();
        }

        return currentBatch.next();
    }
}
