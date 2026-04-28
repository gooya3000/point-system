package com.example.pointsystem.infrastructure.redis;

import com.example.pointsystem.domain.wallet.PointWallet;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 지갑 조회 캐시 miss 시 분산 락으로 캐시 재생성을 직렬화합니다.
 */
@Component
@RequiredArgsConstructor
public class PointWalletCacheReader {

    private static final String REBUILD_LOCK_PREFIX = "lock:cache:pointWallet:";
    private static final Duration REBUILD_LOCK_WAIT = Duration.ofMillis(150);
    private static final int RETRY_COUNT = 5;
    private static final Duration RETRY_DELAY = Duration.ofMillis(80);

    private final CacheManager cacheManager;
    private final RedissonClient redissonClient;

    public PointWallet getOrLoad(Long memberId, Supplier<PointWallet> loader) {
        Objects.requireNonNull(memberId, "회원 식별자는 비어 있을 수 없습니다.");
        Objects.requireNonNull(loader, "loader 는 비어 있을 수 없습니다.");

        Cache cache = requireCache();
        PointWallet cached = cache.get(memberId, PointWallet.class);
        if (cached != null) {
            return cached;
        }

        RLock lock = redissonClient.getLock(REBUILD_LOCK_PREFIX + memberId);
        boolean locked = false;

        try {
            locked = lock.tryLock(REBUILD_LOCK_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            if (locked) {
                // 다른 인스턴스가 락 해제 직전에 캐시를 채웠을 수 있어 한 번 더 확인한다.
                PointWallet doubleChecked = cache.get(memberId, PointWallet.class);
                if (doubleChecked != null) {
                    return doubleChecked;
                }

                // 캐시 재구성은 락을 획득한 한 요청만 DB를 본다.
                PointWallet loaded = loader.get();
                cache.put(memberId, loaded);
                return loaded;
            }

            // 락을 잡지 못한 요청은 바로 DB로 내려가지 않고 짧게 재조회한다.
            for (int i = 0; i < RETRY_COUNT; i++) {
                sleep(RETRY_DELAY);
                PointWallet retried = cache.get(memberId, PointWallet.class);
                if (retried != null) {
                    return retried;
                }
            }

            // 재조회 구간이 지나도 캐시가 비어 있으면 마지막 수단으로 직접 복구한다.
            PointWallet loaded = loader.get();
            cache.put(memberId, loaded);
            return loaded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("캐시 재생성 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Cache requireCache() {
        Cache cache = cacheManager.getCache(RedisCacheConfig.POINT_WALLET_CACHE);
        if (cache == null) {
            throw new IllegalStateException("pointWallet 캐시를 찾을 수 없습니다.");
        }
        return cache;
    }

    private void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }
}
