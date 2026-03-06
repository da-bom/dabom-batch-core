package com.template.worker.jobs.usagereset.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.reader.ActiveFamilyCursorReader;
import com.template.worker.jobs.usagereset.support.MonthlyUsageResetProperties;

@Component
public class ActiveFamilyReader extends ActiveFamilyCursorReader {

    public ActiveFamilyReader(JdbcTemplate jdbcTemplate, MonthlyUsageResetProperties properties) {
        super(jdbcTemplate, properties::getDbFetchSize);
    }
}
