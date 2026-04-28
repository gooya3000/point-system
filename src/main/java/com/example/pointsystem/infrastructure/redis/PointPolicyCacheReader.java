package com.example.pointsystem.infrastructure.redis;

import com.example.pointsystem.domain.policy.PointPolicy;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 정책/메타데이터 캐시에 TTL jitter 와 stale-while-revalidate 전략을 적용합니다.
 */
@Component
@RequiredArgsConstructor
public class PointPolicyCacheReader {

    private static final String POLICY_CACHE_KEY = "cache:pointPolicy:current";
    private static final String POLICY_LOCK_KEY = "lock:cache:pointPolicy:current";
    private static final Duration BASE_TTL = Duration.ofMinutes(10);
    private static final Duration JITTER_MAX = Duration.ofMinutes(2);
    private static final Duration STALE_WINDOW = Duration.ofMinutes(3);
    private static final Duration LOCK_WAIT = Duration.ofMillis(150);
    private static final int RETRY_COUNT = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(60);

    private final RedissonClient redissonClient;

    public PointPolicy getCurrentPolicy(Supplier<PointPolicy> loader) {
        Objects.requireNonNull(loader, "loader 는 비어 있을 수 없습니다.");

        RBucket<CachedPointPolicy> bucket = redissonClient.getBucket(POLICY_CACHE_KEY);
        CachedPointPolicy cached = bucket.get();
        LocalDateTime now = LocalDateTime.now();

        if (cached != null && cached.isFresh(now)) {
            return cached.pointPolicy();
        }

        if (cached != null && cached.isStale(now)) {
            // stale 구간에서는 이전 값을 우선 반환하고, 갱신은 백그라운드에서 시도한다.
            refreshInBackground(bucket, loader);
            return cached.pointPolicy();
        }

        // stale window 밖으로 벗어난 hard miss 는 동기적으로 재구성한다.
        return rebuildOnMiss(bucket, loader, now);
    }

    private PointPolicy rebuildOnMiss(RBucket<CachedPointPolicy> bucket, Supplier<PointPolicy> loader, LocalDateTime now) {
        RLock lock = redissonClient.getLock(POLICY_LOCK_KEY);
        boolean locked = false;

        try {
            locked = lock.tryLock(LOCK_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            if (locked) {
                // 락 대기 중 다른 인스턴스가 복구했을 수 있어 다시 확인한다.
                CachedPointPolicy doubleChecked = bucket.get();
                if (doubleChecked != null && !doubleChecked.isExpired(LocalDateTime.now())) {
                    return doubleChecked.pointPolicy();
                }

                // hard miss 에서는 락을 잡은 한 요청만 DB 조회 후 캐시를 채운다.
                PointPolicy loaded = loader.get();
                put(bucket, loaded);
                return loaded;
            }

            // 다른 인스턴스가 재구성 중이면 잠깐 재조회해 캐시가 채워지길 기다린다.
            for (int i = 0; i < RETRY_COUNT; i++) {
                sleep(RETRY_DELAY);
                CachedPointPolicy retried = bucket.get();
                if (retried != null && !retried.isExpired(LocalDateTime.now())) {
                    return retried.pointPolicy();
                }
            }

            PointPolicy loaded = loader.get();
            put(bucket, loaded);
            return loaded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("정책 캐시 재생성 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void refreshInBackground(RBucket<CachedPointPolicy> bucket, Supplier<PointPolicy> loader) {
        CompletableFuture.runAsync(() -> {
            RLock lock = redissonClient.getLock(POLICY_LOCK_KEY);
            boolean locked = false;
            try {
                locked = lock.tryLock(0, TimeUnit.MILLISECONDS);
                if (!locked) {
                    return;
                }

                // 이미 다른 스레드가 fresh 상태로 갱신했으면 중복 갱신하지 않는다.
                CachedPointPolicy doubleChecked = bucket.get();
                if (doubleChecked != null && doubleChecked.isFresh(LocalDateTime.now())) {
                    return;
                }

                PointPolicy loaded = loader.get();
                put(bucket, loaded);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
    }

    private void put(RBucket<CachedPointPolicy> bucket, PointPolicy pointPolicy) {
        LocalDateTime now = LocalDateTime.now();
        Duration jitter = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(JITTER_MAX.toSeconds() + 1));
        LocalDateTime freshUntil = now.plus(BASE_TTL).plus(jitter);
        LocalDateTime staleUntil = freshUntil.plus(STALE_WINDOW);
        // fresh 구간과 stale 구간을 분리해 "즉시 갱신 필요"와 "조금 오래된 값 허용"을 구분한다.
        CachedPointPolicy cached = new CachedPointPolicy(pointPolicy, freshUntil, staleUntil);
        bucket.set(cached, Duration.between(now, staleUntil));
    }

    private void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    record CachedPointPolicy(
            PointPolicy pointPolicy,
            LocalDateTime freshUntil,
            LocalDateTime staleUntil
    ) implements Serializable {

        boolean isFresh(LocalDateTime now) {
            return !freshUntil.isBefore(now);
        }

        boolean isStale(LocalDateTime now) {
            return freshUntil.isBefore(now) && !staleUntil.isBefore(now);
        }

        boolean isExpired(LocalDateTime now) {
            return staleUntil.isBefore(now);
        }
    }
}
