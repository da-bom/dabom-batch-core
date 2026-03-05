package com.template.worker.jobs.usagereset.reader;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ActiveFamilyMemberReader
        implements ItemReader<FamilyMemberUsageResetTarget>, StepExecutionListener {

    private static final String READ_ACTIVE_FAMILY_MEMBER_SQL =
            """
            SELECT id, family_id, customer_id
            FROM family_member
            WHERE deleted_at IS NULL
              AND id > ?
            ORDER BY id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MonthlyUsageResetProperties properties;

    private Iterator<FamilyMemberUsageResetTarget> currentBatch = Collections.emptyIterator();
    private long lastFamilyMemberId = 0L;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // Step 재실행 시 상태 누수를 막기 위해 커서를 초기화함
        currentBatch = Collections.emptyIterator();
        lastFamilyMemberId = 0L;
    }

    @Override
    public FamilyMemberUsageResetTarget read() {
        while (!currentBatch.hasNext()) {
            // PK 커서 기반으로 활성 구성원 목록을 배치 단위 조회함
            List<FamilyMemberRow> familyMembers =
                    jdbcTemplate.query(
                            READ_ACTIVE_FAMILY_MEMBER_SQL,
                            (resultSet, rowNum) ->
                                    new FamilyMemberRow(
                                            resultSet.getLong("id"),
                                            resultSet.getLong("family_id"),
                                            resultSet.getLong("customer_id")),
                            lastFamilyMemberId,
                            properties.getDbFetchSize());

            // 더 이상 데이터가 없으면 null을 반환해 Chunk 처리를 종료함
            if (familyMembers.isEmpty()) {
                return null;
            }

            lastFamilyMemberId = familyMembers.get(familyMembers.size() - 1).id();

            // Reader 책임을 유지하기 위해 writer 입력 DTO로만 변환함
            List<FamilyMemberUsageResetTarget> targets =
                    familyMembers.stream()
                            .map(
                                    row ->
                                            new FamilyMemberUsageResetTarget(
                                                    row.familyId(), row.customerId()))
                            .toList();

            currentBatch = targets.iterator();
        }

        return currentBatch.next();
    }

    private record FamilyMemberRow(Long id, Long familyId, Long customerId) {}
}
