package com.template.worker.jobs.recap.weekly.reader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.template.worker.jobs.common.reader.ActiveFamilyCursorReader;
import com.template.worker.jobs.recap.weekly.support.WeeklyFamilyRecapProperties;

@Component
public class WeeklyFamilyRecapFamilyReader extends ActiveFamilyCursorReader {

    public WeeklyFamilyRecapFamilyReader(
            JdbcTemplate jdbcTemplate, WeeklyFamilyRecapProperties properties) {
        // 공통 활성 가족 커서 리더를 재사용
        super(jdbcTemplate, properties::getDbFetchSize);
    }
}
