package com.example.pointsystem.infrastructure.jpa.ledger;

import com.example.pointsystem.domain.ledger.PointLedger;

/**
 * 원장 도메인 모델과 JPA 엔티티를 변환합니다.
 */
public final class PointLedgerMapper {

    private PointLedgerMapper() {
    }

    public static PointLedgerEntity toEntity(PointLedger ledger) {
        PointLedgerEntity entity = new PointLedgerEntity(
                ledger.getEventId(),
                ledger.getEventType(),
                ledger.getMemberId(),
                ledger.getUsageId(),
                ledger.getEarnedPointId(),
                ledger.getAmount(),
                ledger.getOrderNo(),
                ledger.getOccurredAt(),
                ledger.getCreatedAt()
        );
        entity.setId(ledger.getLedgerId());
        return entity;
    }

    public static PointLedger toDomain(PointLedgerEntity entity) {
        return new PointLedger(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getMemberId(),
                entity.getUsageId(),
                entity.getEarnedPointId(),
                entity.getAmount(),
                entity.getOrderNo(),
                entity.getOccurredAt(),
                entity.getCreatedAt()
        );
    }
}
