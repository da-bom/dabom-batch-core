package com.template.worker.jobs.recap.weekly.tasklet;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
public class WeeklyFamilyRecapUpsertCompletionTasklet implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 집계 스텝 이후 완료 마커 스텝
        return RepeatStatus.FINISHED;
    }
}
