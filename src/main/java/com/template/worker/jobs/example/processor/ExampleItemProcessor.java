package com.template.worker.jobs.example.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/** 읽어온 데이터를 가공/변환하는 역할을 한다. */
@Component
public class ExampleItemProcessor implements ItemProcessor<String, String> {
    @Override
    public String process(String item) throws Exception {
        return null;
    }
}
