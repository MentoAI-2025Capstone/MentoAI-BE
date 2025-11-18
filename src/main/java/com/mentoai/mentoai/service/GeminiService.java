package com.mentoai.mentoai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String EMBEDDING_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/embedding-001:embedContent";
    private static final String TEXT_GENERATION_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";

    /**
     * 텍스트를 임베딩 벡터로 변환
     */
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text is required.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "models/embedding-001");
            
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(Map.of("text", text)));
            requestBody.put("content", content);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    EMBEDDING_API_URL + "?key=" + apiKey,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode embeddingNode = jsonNode.path("embedding").path("values");
                
                List<Double> embedding = new ArrayList<>();
                for (JsonNode value : embeddingNode) {
                    embedding.add(value.asDouble());
                }
                return embedding;
            } else {
                log.error("Gemini API embedding request failed: {}", response.getStatusCode());
                throw new RuntimeException("Failed to generate embedding");
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API for embedding", e);
            throw new RuntimeException("Error generating embedding: " + e.getMessage(), e);
        }
    }

    /**
     * 여러 텍스트의 임베딩을 일괄 생성
     */
    public Map<String, List<Double>> generateEmbeddings(List<String> texts) {
        Map<String, List<Double>> embeddings = new HashMap<>();
        for (String text : texts) {
            try {
                embeddings.put(text, generateEmbedding(text));
            } catch (Exception e) {
                log.warn("Failed to generate embedding for text '{}': {}", text, e.getMessage());
            }
        }
        return embeddings;
    }

    /**
     * 두 임베딩 벡터의 코사인 유사도 계산
     */
    public double cosineSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1 == null || embedding2 == null || 
            embedding1.size() != embedding2.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.size(); i++) {
            dotProduct += embedding1.get(i) * embedding2.get(i);
            norm1 += embedding1.get(i) * embedding1.get(i);
            norm2 += embedding2.get(i) * embedding2.get(i);
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        return denominator == 0.0 ? 0.0 : dotProduct / denominator;
    }

    /**
     * 텍스트 생성 (프롬프트 기반)
     */
    public String generateText(String prompt) {
        return generateText(prompt, null);
    }

    /**
     * 대화 컨텍스트를 포함한 텍스트 생성
     * @param userMessage 사용자의 현재 메시지
     * @param conversationHistory 대화 기록 (역순: 최신이 먼저)
     */
    public String generateText(String userMessage, List<ChatMessage> conversationHistory) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            List<Map<String, Object>> contents = new ArrayList<>();
            
            // 대화 기록이 있으면 추가 (역순이므로 뒤집어서 추가)
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                List<ChatMessage> reversed = new ArrayList<>(conversationHistory);
                Collections.reverse(reversed);
                
                for (ChatMessage msg : reversed) {
                    Map<String, Object> content = new HashMap<>();
                    content.put("role", msg.role().equals("USER") ? "user" : "model");
                    content.put("parts", List.of(Map.of("text", msg.content())));
                    contents.add(content);
                }
            }
            
            // 현재 사용자 메시지 추가
            Map<String, Object> userContent = new HashMap<>();
            userContent.put("role", "user");
            userContent.put("parts", List.of(Map.of("text", userMessage)));
            contents.add(userContent);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contents);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    TEXT_GENERATION_API_URL + "?key=" + apiKey,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                return jsonNode.path("candidates")
                        .get(0)
                        .path("content")
                        .path("parts")
                        .get(0)
                        .path("text")
                        .asText();
            } else {
                log.error("Gemini API text generation request failed: {}", response.getStatusCode());
                throw new RuntimeException("Failed to generate text");
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API for text generation", e);
            throw new RuntimeException("Error generating text: " + e.getMessage(), e);
        }
    }

    /**
     * 대화 메시지 레코드
     */
    public record ChatMessage(String role, String content) {
    }
}


