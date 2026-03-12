package com.template.worker.jobs.recap.monthly.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.reader.ActiveFamilyCursorReader;
import com.template.worker.jobs.recap.monthly.support.MonthlyFamilyRecapProperties;

@Component
public class MonthlyFamilyRecapFamilyReader extends ActiveFamilyCursorReader {

    public MonthlyFamilyRecapFamilyReader(
            JdbcTemplate jdbcTemplate, MonthlyFamilyRecapProperties properties) {
        // 공통 활성 가족 커서 리더를 재사용
        super(jdbcTemplate, properties::getDbFetchSize);
    }
}
