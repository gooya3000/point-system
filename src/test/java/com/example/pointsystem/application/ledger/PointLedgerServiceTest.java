package com.example.pointsystem.application.ledger;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.event.PointEventType;
import com.example.pointsystem.domain.ledger.PointLedger;
import com.example.pointsystem.domain.ledger.PointLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointLedgerServiceTest {

    @Mock
    private PointLedgerRepository pointLedgerRepository;

    private PointLedgerService service;

    @BeforeEach
    void setUp() {
        service = new PointLedgerService(pointLedgerRepository);
    }

    @Test
    // 의도: 이미 원장에 기록된 이벤트는 다시 저장하지 않는다.
    void record_skips_when_event_id_already_exists() {
        PointChangedEvent event = event("event-1");
        when(pointLedgerRepository.existsByEventId("event-1")).thenReturn(true);

        service.record(event);

        verify(pointLedgerRepository, never()).save(any(PointLedger.class));
    }

    @Test
    // 의도: 신규 이벤트는 원장으로 변환해 저장한다.
    void record_saves_when_event_id_is_new() {
        PointChangedEvent event = event("event-2");
        when(pointLedgerRepository.existsByEventId("event-2")).thenReturn(false);

        service.record(event);

        verify(pointLedgerRepository).save(any(PointLedger.class));
    }

    @Test
    // 의도: 동시 중복 소비로 유니크 제약이 발생해도 멱등 처리로 간주한다.
    void record_ignores_duplicate_key_race() {
        PointChangedEvent event = event("event-3");
        when(pointLedgerRepository.existsByEventId("event-3")).thenReturn(false);
        when(pointLedgerRepository.save(any(PointLedger.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate event_id"));

        service.record(event);

        verify(pointLedgerRepository).save(any(PointLedger.class));
    }

    private PointChangedEvent event(String eventId) {
        return new PointChangedEvent(
                eventId,
                PointEventType.USE,
                1L,
                2L,
                null,
                500,
                "ORDER-1",
                LocalDateTime.now()
        );
    }
}
