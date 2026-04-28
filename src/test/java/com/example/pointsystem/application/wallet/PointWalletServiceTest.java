package com.example.pointsystem.application.wallet;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.policy.PointPolicyService;
import com.example.pointsystem.domain.wallet.*;
import com.example.pointsystem.infrastructure.redis.PointWalletCacheReader;
import com.example.pointsystem.infrastructure.redis.PointUseIdempotencyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointWalletServiceTest {

    @Mock
    private PointWalletRepository pointWalletRepository;
    @Mock
    private PointUsageRepository pointUsageRepository;
    @Mock
    private PointPolicyService pointPolicyService;
    @Mock
    private PointUseIdempotencyManager pointUseIdempotencyManager;
    @Mock
    private PointWalletCacheReader pointWalletCacheReader;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private PointWalletService service;

    @BeforeEach
    void setUp() {
        service = new PointWalletService(
                pointWalletRepository,
                pointUsageRepository,
                pointPolicyService,
                pointUseIdempotencyManager,
                pointWalletCacheReader,
                applicationEventPublisher
        );
    }

    @Test
    // 의도: 동일 요청(orderNo)에 대한 재시도 시 기존 사용 이력을 반환해 중복 차감을 막는다.
    void usePoint_returns_existing_usage_when_idempotency_key_exists() {
        PointUsage existing = new PointUsage(
                10L,
                1L,
                "ORDER-1",
                300,
                List.of(PointUsageDetail.of(100L, 300)),
                LocalDateTime.now()
        );

        when(pointUseIdempotencyManager.findUsageId(1L, "ORDER-1")).thenReturn(Optional.of(10L));
        when(pointUsageRepository.findById(10L)).thenReturn(Optional.of(existing));

        PointUsage result = service.usePoint(1L, 300, "ORDER-1");

        assertEquals(10L, result.getUsageId());
        verify(pointWalletRepository, never()).findByMemberId(any());
        verify(pointWalletRepository, never()).save(any());
        verify(pointUsageRepository, never()).save(any());
        verify(pointUseIdempotencyManager, never()).saveUsageId(any(), any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any(PointChangedEvent.class));
    }

    @Test
    // 의도: Redis 멱등 키가 stale인 경우 키를 정리하고 신규 사용 처리로 복구한다.
    void usePoint_clears_stale_idempotency_key_and_processes_new_usage() {
        PointWallet wallet = walletWithBalance(1L, 1000);

        when(pointUseIdempotencyManager.findUsageId(1L, "ORDER-2")).thenReturn(Optional.of(99L));
        when(pointUsageRepository.findById(99L)).thenReturn(Optional.empty());
        when(pointWalletRepository.findByMemberId(1L)).thenReturn(Optional.of(wallet));
        when(pointUsageRepository.save(any(PointUsage.class))).thenAnswer(invocation -> {
            PointUsage usage = invocation.getArgument(0);
            return new PointUsage(
                    20L,
                    usage.getMemberId(),
                    usage.getOrderNo(),
                    usage.getUsedAmount(),
                    usage.getDetails(),
                    usage.getCreatedAt()
            );
        });

        PointUsage result = service.usePoint(1L, 200, "ORDER-2");

        assertEquals(20L, result.getUsageId());
        verify(pointUseIdempotencyManager).clear(1L, "ORDER-2");
        verify(pointWalletRepository).save(any(PointWallet.class));
        verify(pointUseIdempotencyManager).saveUsageId(1L, "ORDER-2", 20L);
        verify(applicationEventPublisher).publishEvent(any(PointChangedEvent.class));
    }

    private PointWallet walletWithBalance(Long memberId, int amount) {
        EarnedPoint earnedPoint = new EarnedPoint(
                1L,
                amount,
                amount,
                LocalDateTime.now().plusDays(1),
                EarnedPointSourceType.NORMAL,
                EarnedPointStatus.ACTIVE,
                LocalDateTime.now()
        );
        return new PointWallet(memberId, new ArrayList<>(List.of(earnedPoint)));
    }
}
