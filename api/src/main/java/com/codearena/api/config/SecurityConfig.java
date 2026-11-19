package com.codearena.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 세션 없는(stateless) JWT 인증.
 * 토큰 추출/검증/SecurityContext 설정은 Spring Security의 OAuth2 Resource Server가 처리한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 쿠키 세션을 쓰지 않고 Authorization 헤더로만 인증하므로 CSRF 보호가 필요 없다
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ping", "/api/problems", "/api/problems/*",
                                "/api/submissions/*", "/actuator/health").permitAll()
                        // 처리되지 않은 예외는 /error로 포워드된다. 막아두면 500이 401로 바뀌어 원인이 가려진다
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** 기본은 BCrypt. 저장값에 {bcrypt} 접두사가 붙어 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
