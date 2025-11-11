package com.mentoai.mentoai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.Google.class, AuthProperties.Jwt.class})
public class AuthProperties {

    @ConfigurationProperties(prefix = "application.auth.google")
    public record Google(
            String redirectUri,
            String redirectUriLocal
    ) {
    }

    @ConfigurationProperties(prefix = "application.auth.jwt")
    public record Jwt(
            String secret,
            long accessTokenMinutes,
            long refreshTokenDays
    ) {
    }
}
