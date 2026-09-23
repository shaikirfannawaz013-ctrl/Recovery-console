package com.prp.security;

import com.prp.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/** Opaque refresh tokens kept in Redis so they can be rotated and revoked. */
@Component
public class RefreshTokenStore {

    private static final String PREFIX = "refresh:";
    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final AppProperties props;

    public RefreshTokenStore(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.props = props;
    }

    public String issue(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), props.jwt().refreshTtl());
        return token;
    }

    /** Single use: the token is deleted as it is read (rotation). */
    public Optional<Long> consume(String token) {
        String userId = redis.opsForValue().getAndDelete(PREFIX + token);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    public void revoke(String token) {
        redis.delete(PREFIX + token);
    }
}
