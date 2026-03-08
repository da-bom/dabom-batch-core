package com.template.worker.jobs.usagereset.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.reader.ActiveFamilyMemberCursorReader;
import com.template.worker.jobs.usagereset.model.FamilyMemberUsageResetTarget;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;

@Component
public class ActiveFamilyMemberReader
        extends ActiveFamilyMemberCursorReader<FamilyMemberUsageResetTarget> {

    public ActiveFamilyMemberReader(
            JdbcTemplate jdbcTemplate, MonthlyUsageResetProperties properties) {
        super(jdbcTemplate, properties::getDbFetchSize, FamilyMemberUsageResetTarget::new);
    }
}
