package com.example.pointsystem.infrastructure.jpa.ledger;

import com.example.pointsystem.application.event.PointEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 포인트 원장 JPA 엔티티입니다.
 */
@Entity
@Getter
@Table(
        name = "point_ledger",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_point_ledger_event_id", columnNames = "event_id")
        }
)
@NoArgsConstructor
public class PointLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PointEventType eventType;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "usage_id")
    private Long usageId;

    @Column(name = "earned_point_id")
    private Long earnedPointId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "order_no", length = 100)
    private String orderNo;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PointLedgerEntity(
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

    void setId(Long id) {
        this.id = id;
    }
}
