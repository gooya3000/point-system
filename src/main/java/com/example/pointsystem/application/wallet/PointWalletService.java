package com.example.pointsystem.application.wallet;

import com.example.pointsystem.application.event.PointChangedEvent;
import com.example.pointsystem.application.event.PointEventType;
import com.example.pointsystem.application.policy.PointPolicyService;
import com.example.pointsystem.domain.policy.PointPolicy;
import com.example.pointsystem.domain.wallet.*;
import com.example.pointsystem.infrastructure.redis.MemberPointLock;
import com.example.pointsystem.infrastructure.redis.PointWalletCacheReader;
import com.example.pointsystem.infrastructure.redis.PointUseIdempotencyManager;
import com.example.pointsystem.infrastructure.redis.RedisCacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 회원 포인트 지갑의 적립, 사용, 조회 기능을 제공하는 응용 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class PointWalletService {

    private final PointWalletRepository pointWalletRepository;
    private final PointUsageRepository pointUsageRepository;
    private final PointPolicyService pointPolicyService;
    private final PointUseIdempotencyManager pointUseIdempotencyManager;
    private final PointWalletCacheReader pointWalletCacheReader;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 포인트를 적립합니다.
     * @param memberId 회원 ID
     * @param amount 금액
     * @param expireAt 만료일시
     * @param sourceType 적립 포인트 발생 유형
     */
    @Transactional
    @MemberPointLock(key = "#p0")
    @CacheEvict(cacheNames = RedisCacheConfig.POINT_WALLET_CACHE, key = "#memberId")
    public void earnPoint(Long memberId, int amount, LocalDateTime expireAt, String sourceType) {
        // 1회 적립 한도, 최대 보유 한도 정책 가져오기
        PointPolicy policy = pointPolicyService.getCurrentPolicy();

        PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                .orElseGet(() -> PointWallet.createWallet(memberId));

        wallet.earn(amount, expireAt, EarnedPointSourceType.fromNullable(sourceType), policy);

        pointWalletRepository.save(wallet);
        applicationEventPublisher.publishEvent(PointChangedEvent.of(
                PointEventType.EARN,
                memberId,
                null,
                null,
                amount,
                null
        ));
    }

    /**
     * 지갑을 조회합니다.
     * @param memberId 회원 ID
     * @return PointWallet
     */
    @Transactional(readOnly = true)
    public PointWallet getWallet(Long memberId) {
        return pointWalletCacheReader.getOrLoad(memberId,
                () -> pointWalletRepository.findByMemberId(memberId)
                        .orElseGet(() -> PointWallet.createWallet(memberId))
        );
    }

    /**
     * 포인트를 사용합니다.
     * @param memberId 회원 ID
     * @param amount 금액
     * @param orderNo 주문번호
     * @return PointUsage
     */
    @Transactional
    @MemberPointLock(key = "#p0")
    @CacheEvict(cacheNames = RedisCacheConfig.POINT_WALLET_CACHE, key = "#memberId")
    public PointUsage usePoint(Long memberId, int amount, String orderNo) {
        Optional<Long> existingUsageId = pointUseIdempotencyManager.findUsageId(memberId, orderNo);
        if (existingUsageId.isPresent()) {
            Optional<PointUsage> existingUsage = pointUsageRepository.findById(existingUsageId.get());
            if (existingUsage.isPresent()) {
                return existingUsage.get();
            }
            pointUseIdempotencyManager.clear(memberId, orderNo);
        }

        PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                .orElseGet(() -> PointWallet.createWallet(memberId));

        PointUsage usage = wallet.use(amount, orderNo); // 도메인 호출

        pointWalletRepository.save(wallet);
        PointUsage savedUsage = pointUsageRepository.save(usage);
        pointUseIdempotencyManager.saveUsageId(memberId, orderNo, savedUsage.getUsageId());
        applicationEventPublisher.publishEvent(PointChangedEvent.of(
                PointEventType.USE,
                memberId,
                savedUsage.getUsageId(),
                null,
                savedUsage.getUsedAmount(),
                savedUsage.getOrderNo()
        ));
        return savedUsage;
    }

    /**
     * 적립을 취소합니다.
     */
    @Transactional
    @MemberPointLock(key = "#p0")
    @CacheEvict(cacheNames = RedisCacheConfig.POINT_WALLET_CACHE, key = "#memberId")
    public void cancelEarn(Long memberId, Long earnedPointId) {
        PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원의 지갑을 찾을 수 없습니다. 회원 식별자=" + memberId));

        EarnedPoint canceled = wallet.cancelEarn(earnedPointId);
        pointWalletRepository.save(wallet);
        applicationEventPublisher.publishEvent(PointChangedEvent.of(
                PointEventType.EARN_CANCEL,
                memberId,
                null,
                earnedPointId,
                canceled.getAmount(),
                null
        ));
    }

    /**
     * 포인트 사용을 부분 취소합니다.
     */
    @Transactional
    @MemberPointLock(key = "#p0")
    @CacheEvict(cacheNames = RedisCacheConfig.POINT_WALLET_CACHE, key = "#memberId")
    public PointUsage cancelUse(Long memberId, Long usageId, int cancelAmount) {
        PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원의 지갑을 찾을 수 없습니다. 회원 식별자=" + memberId));

        PointUsage usage = pointUsageRepository.findById(usageId)
                .orElseThrow(() -> new IllegalArgumentException("사용 이력을 찾을 수 없습니다. 사용 식별자=" + usageId));

        PointUsage canceled = wallet.cancelUse(usage, cancelAmount);

        pointWalletRepository.save(wallet);
        PointUsage savedUsage = pointUsageRepository.save(canceled);
        applicationEventPublisher.publishEvent(PointChangedEvent.of(
                PointEventType.USE_CANCEL_PARTIAL,
                memberId,
                savedUsage.getUsageId(),
                null,
                cancelAmount,
                savedUsage.getOrderNo()
        ));
        return savedUsage;
    }

    /**
     * 포인트 사용을 전액 취소합니다.
     */
    @Transactional
    @MemberPointLock(key = "#p0")
    @CacheEvict(cacheNames = RedisCacheConfig.POINT_WALLET_CACHE, key = "#memberId")
    public PointUsage cancelUseAll(Long memberId, Long usageId) {
        PointWallet wallet = pointWalletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원의 지갑을 찾을 수 없습니다. 회원 식별자=" + memberId));

        PointUsage usage = pointUsageRepository.findById(usageId)
                .orElseThrow(() -> new IllegalArgumentException("사용 이력을 찾을 수 없습니다. 사용 식별자=" + usageId));

        int cancelAmount = usage.getUsedAmount();
        PointUsage canceled = wallet.cancelUseAll(usage);

        pointWalletRepository.save(wallet);
        PointUsage savedUsage = pointUsageRepository.save(canceled);
        applicationEventPublisher.publishEvent(PointChangedEvent.of(
                PointEventType.USE_CANCEL_ALL,
                memberId,
                savedUsage.getUsageId(),
                null,
                cancelAmount,
                savedUsage.getOrderNo()
        ));
        return savedUsage;
    }

}
