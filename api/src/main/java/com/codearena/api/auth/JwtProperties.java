package com.codearena.api.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codearena.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {
}
