package com.template.worker.jobs.usagereset.support;

import java.time.Duration;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonthlyResetLockManager {

    private static final DefaultRedisScript<Long> LOCK_RELEASE_SCRIPT = buildLockReleaseScript();

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(String lockKey, String lockOwner, Duration ttl) {
        // SET NX EX 동작으로 단일 실행 락을 획득함
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockOwner, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseIfOwner(String lockKey, String lockOwner) {
        if (lockKey == null || lockOwner == null) {
            return false;
        }

        // Lua로 소유자 확인 후 삭제해 안전하게 락을 해제함
        Long released =
                redisTemplate.execute(
                        LOCK_RELEASE_SCRIPT, Collections.singletonList(lockKey), lockOwner);
        return released != null && released > 0;
    }

    private static DefaultRedisScript<Long> buildLockReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        // 락 해제 원자성을 보장하기 위해 GET+DEL을 Lua로 묶음
        // 내가 잡은 락이면 지우고 아니면 지우지 않기
        script.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] "
                        + "then return redis.call('DEL', KEYS[1]) else return 0 end");
        return script;
    }
}
