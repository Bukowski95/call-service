package com.onextel.CallServiceApplication.service.webhook;

import com.onextel.CallServiceApplication.dto.WebhookDeliveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookDeliveryService {
    private final static String ALGORITHM_HMAC_SHA256 = "HmacSHA256";
    private final RestTemplate restTemplate;
    private final Clock clock;

    public WebhookDeliveryResult deliver(String url, String secret, String payload) {
        try {
            Instant start = clock.instant();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", generateSignature(payload, secret));

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            return WebhookDeliveryResult.success(
                    response.getStatusCode().value(),
                    response.getBody(),
                    Duration.between(start, clock.instant())
            );
        } catch (Exception e) {
            log.warn("Webhook delivery failed to {}", url, e);
            return WebhookDeliveryResult.failure(e.getMessage());
        }
    }

    private String generateSignature(String payload, String secret) {
        try {
            Mac hmac = Mac.getInstance(ALGORITHM_HMAC_SHA256);
            hmac.init(new SecretKeySpec(secret.getBytes(), ALGORITHM_HMAC_SHA256));
            byte[] signature = hmac.doFinal(payload.getBytes());
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            log.error("Failed to generate signature {}", secret, e);
            throw new SecurityException("Failed to generate signature", e);
        }
    }

    public boolean verifySignature(String payload, String signature, String secret) {
        String computedSignature = generateSignature(payload, secret);
        return MessageDigest.isEqual(
                computedSignature.getBytes(),
                signature.getBytes()
        );
    }
}