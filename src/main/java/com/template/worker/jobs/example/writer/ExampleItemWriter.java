package com.template.worker.jobs.example.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/** 처리된 결과를 DB/S3/파일 등으로 저장한다. */
@Component
public class ExampleItemWriter implements ItemWriter<String> {
    @Override
    public void write(Chunk<? extends String> chunk) throws Exception {
        chunk.forEach(System.out::println);
    }
}
