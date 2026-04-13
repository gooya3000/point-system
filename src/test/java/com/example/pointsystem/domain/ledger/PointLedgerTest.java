package com.example.pointsystem.domain.ledger;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.event.PointEventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointLedgerTest {

    @Test
    // 의도: 원장은 적립 이벤트를 양수 금액으로 기록한다.
    void from_records_earn_event_as_positive_amount() {
        PointChangedEvent event = event(PointEventType.EARN, 100);

        PointLedger ledger = PointLedger.from(event);

        assertEquals(100, ledger.getAmount());
    }

    @Test
    // 의도: 원장은 사용 이벤트를 음수 금액으로 기록한다.
    void from_records_use_event_as_negative_amount() {
        PointChangedEvent event = event(PointEventType.USE, 100);

        PointLedger ledger = PointLedger.from(event);

        assertEquals(-100, ledger.getAmount());
    }

    @Test
    // 의도: 원장은 사용 취소 이벤트를 양수 금액으로 기록한다.
    void from_records_use_cancel_event_as_positive_amount() {
        PointChangedEvent event = event(PointEventType.USE_CANCEL_PARTIAL, 100);

        PointLedger ledger = PointLedger.from(event);

        assertEquals(100, ledger.getAmount());
    }

    private PointChangedEvent event(PointEventType eventType, int amount) {
        return new PointChangedEvent(
                "event-1",
                eventType,
                1L,
                2L,
                3L,
                amount,
                "ORDER-1",
                LocalDateTime.now()
        );
    }
}
