package com.template.worker.jobs.reconciliation.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.reader.ActiveFamilyMemberCursorReader;
import com.template.worker.jobs.reconciliation.model.FamilyMemberReconciliationTarget;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;

@Component
public class ReconciliationFamilyMemberReader
        extends ActiveFamilyMemberCursorReader<FamilyMemberReconciliationTarget> {

    public ReconciliationFamilyMemberReader(
            JdbcTemplate jdbcTemplate, DbRedisReconciliationProperties properties) {
        super(jdbcTemplate, properties::getDbFetchSize, FamilyMemberReconciliationTarget::new);
    }
}
