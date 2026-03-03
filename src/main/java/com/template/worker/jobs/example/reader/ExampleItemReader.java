package com.template.worker.jobs.example.reader;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.stereotype.Component;

/** DB, S3, API 등에서 데이터를 읽어오는 역할을 한다. */
@Component
public class ExampleItemReader implements ItemReader<String> {
    @Override
    public String read()
            throws Exception,
                    UnexpectedInputException,
                    ParseException,
                    NonTransientResourceException {
        return null;
    }
}
