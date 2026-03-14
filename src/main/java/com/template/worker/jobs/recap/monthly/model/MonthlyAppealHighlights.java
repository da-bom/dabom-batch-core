package com.template.worker.jobs.recap.monthly.model;

import java.util.List;

// 월간 이의제기 하이라이트 JSON 원본 모델
public record MonthlyAppealHighlights(
        TopSuccessfulRequester topSuccessfulRequester, TopAcceptedApprover topAcceptedApprover) {

    public static MonthlyAppealHighlights empty() {
        return new MonthlyAppealHighlights(
                TopSuccessfulRequester.empty(), TopAcceptedApprover.empty());
    }

    public record TopSuccessfulRequester(
            Long requesterId,
            String requesterName,
            int approvedAppealCount,
            List<RecentApprovedAppeal> recentApprovedAppeals) {

        public static TopSuccessfulRequester empty() {
            return new TopSuccessfulRequester(null, null, 0, List.of());
        }
    }

    public record RecentApprovedAppeal(
            Long appealId,
            Long approverId,
            String approverName,
            String requestReason,
            String requestedAt) {}

    public record TopAcceptedApprover(
            Long approverId,
            String approverName,
            int approvedAppealCount,
            List<RecentAcceptedAppeal> recentAcceptedAppeals) {

        public static TopAcceptedApprover empty() {
            return new TopAcceptedApprover(null, null, 0, List.of());
        }
    }

    public record RecentAcceptedAppeal(
            Long appealId,
            Long requesterId,
            String requesterName,
            String requestReason,
            String resolvedAt) {}
}
