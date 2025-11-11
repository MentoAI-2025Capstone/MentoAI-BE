package com.mentoai.mentoai.service;

import com.mentoai.mentoai.config.AuthProperties;
import com.mentoai.mentoai.controller.dto.GoogleTokenResponse;
import com.mentoai.mentoai.controller.dto.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String SCOPE = "openid email profile";

    private final RestTemplate restTemplate;
    private final AuthProperties.Google googleProperties;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    public URI buildAuthorizationUri(String state, boolean useLocalRedirect) {
        String redirectUri = resolveRedirectUri(useLocalRedirect);
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build(true)
                .toUri();
    }

    public GoogleTokenResponse exchangeCodeForToken(String code, boolean useLocalRedirect) {
        String redirectUri = resolveRedirectUri(useLocalRedirect);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<GoogleTokenResponse> response = restTemplate.postForEntity(
                TOKEN_URL,
                new HttpEntity<>(form, headers),
                GoogleTokenResponse.class
        );

        GoogleTokenResponse body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Google token response is empty");
        }
        return body;
    }

    public GoogleUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
                USERINFO_URL,
                org.springframework.http.HttpMethod.GET,
                entity,
                GoogleUserInfo.class
        );

        GoogleUserInfo body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Google user info response is empty");
        }
        return body;
    }

    private String resolveRedirectUri(boolean useLocalRedirect) {
        if (useLocalRedirect && googleProperties.redirectUriLocal() != null && !googleProperties.redirectUriLocal().isBlank()) {
            return googleProperties.redirectUriLocal();
        }
        return googleProperties.redirectUri();
    }
}
