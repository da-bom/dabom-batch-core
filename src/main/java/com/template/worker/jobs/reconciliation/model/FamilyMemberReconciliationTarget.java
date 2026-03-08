package com.template.worker.jobs.reconciliation.model;

// customer monthly usage 키 삭제에 필요한 최소 식별자만 보관함
public record FamilyMemberReconciliationTarget(Long familyId, Long customerId) {}
