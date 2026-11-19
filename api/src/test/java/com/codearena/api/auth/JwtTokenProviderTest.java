package com.codearena.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import com.codearena.api.config.JwtConfig;
import com.codearena.api.member.Member;

/** 스프링 컨텍스트 없이 실제 인코더/디코더로 발급 → 검증 왕복을 확인한다. */
class JwtTokenProviderTest {

    JwtConfig config = new JwtConfig();
    JwtProperties props = new JwtProperties("test-secret-0123456789-0123456789-abc", Duration.ofMinutes(30));
    SecretKey key = config.jwtSecretKey(props);
    JwtTokenProvider provider = new JwtTokenProvider(config.jwtEncoder(key), props);

    private Member member(long id) {
        Member m = new Member("user@example.com", "{noop}pw", "tester");
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void issuedTokenIsAcceptedByDecoder() {
        String token = provider.issueAccessToken(member(7L));

        var jwt = config.jwtDecoder(key).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("nickname")).isEqualTo("tester");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtTokenProvider.ISSUER);
        assertThat(jwt.getHeaders()).containsEntry("typ", "JWT");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = provider.issueAccessToken(member(7L));
        var otherKey = config.jwtSecretKey(new JwtProperties("another-secret-9876543210-9876543210-zz", Duration.ofMinutes(30)));

        assertThatThrownBy(() -> config.jwtDecoder(otherKey).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> config.jwtSecretKey(new JwtProperties("too-short", Duration.ofMinutes(30))))
                .isInstanceOf(IllegalStateException.class);
    }
}
