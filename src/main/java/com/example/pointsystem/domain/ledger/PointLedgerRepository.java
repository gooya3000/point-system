package com.example.pointsystem.domain.ledger;

/**
 * 포인트 원장 저장소 포트입니다.
 */
public interface PointLedgerRepository {

    boolean existsByEventId(String eventId);

    PointLedger save(PointLedger pointLedger);
}
