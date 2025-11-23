package com.mentoai.mentoai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Periodically pings the deployed Render service to keep it awake.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenderKeepAliveScheduler {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keepalive.url:https://mentoai.onrender.com/healthz}")
    private String keepAliveUrl;

    /**
     * Every five minutes (default) send a GET request to keep the Render dyno awake.
     */
    @Scheduled(fixedDelayString = "${keepalive.interval-ms:300000}")
    public void pingRender() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(keepAliveUrl, String.class);
            log.debug("Keep-alive ping succeeded: status={} url={}", response.getStatusCode(), keepAliveUrl);
        } catch (Exception e) {
            log.warn("Keep-alive ping failed for url={}", keepAliveUrl, e);
        }
    }
}

