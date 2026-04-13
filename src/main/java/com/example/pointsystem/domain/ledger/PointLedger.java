package com.example.pointsystem.domain.ledger;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.event.PointEventType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 포인트 변경 사실을 정산/감사 목적으로 기록하는 원장 모델입니다.
 */
@Getter
public class PointLedger {

    private final Long ledgerId;
    private final String eventId;
    private final PointEventType eventType;
    private final Long memberId;
    private final Long usageId;
    private final Long earnedPointId;
    private final int amount;
    private final String orderNo;
    private final LocalDateTime occurredAt;
    private final LocalDateTime createdAt;

    public PointLedger(
            Long ledgerId,
            String eventId,
            PointEventType eventType,
            Long memberId,
            Long usageId,
            Long earnedPointId,
            int amount,
            String orderNo,
            LocalDateTime occurredAt,
            LocalDateTime createdAt
    ) {
        this.ledgerId = ledgerId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.memberId = memberId;
        this.usageId = usageId;
        this.earnedPointId = earnedPointId;
        this.amount = amount;
        this.orderNo = orderNo;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public static PointLedger from(PointChangedEvent event) {
        return new PointLedger(
                null,
                event.eventId(),
                event.eventType(),
                event.memberId(),
                event.usageId(),
                event.earnedPointId(),
                ledgerAmount(event),
                event.orderNo(),
                event.occurredAt(),
                LocalDateTime.now()
        );
    }

    private static int ledgerAmount(PointChangedEvent event) {
        int amount = event.amount() == null ? 0 : event.amount();
        return switch (event.eventType()) {
            case EARN, USE_CANCEL_PARTIAL, USE_CANCEL_ALL -> amount;
            case USE, EARN_CANCEL -> -amount;
        };
    }
}
