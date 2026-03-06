package com.template.worker.jobs.reconciliation.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.reader.ActiveFamilyCursorReader;
import com.template.worker.jobs.reconciliation.support.DbRedisReconciliationProperties;

@Component
public class ReconciliationFamilyReader extends ActiveFamilyCursorReader {

    public ReconciliationFamilyReader(
            JdbcTemplate jdbcTemplate, DbRedisReconciliationProperties properties) {
        super(jdbcTemplate, properties::getDbFetchSize);
    }
}
