package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ChatMessageRequest;
import com.mentoai.mentoai.controller.dto.ChatMessageResponse;
import com.mentoai.mentoai.controller.dto.ChatSessionRequest;
import com.mentoai.mentoai.controller.dto.ChatSessionResponse;
import com.mentoai.mentoai.entity.ChatMessageEntity;
import com.mentoai.mentoai.entity.ChatSessionEntity;
import com.mentoai.mentoai.repository.ChatMessageRepository;
import com.mentoai.mentoai.repository.ChatSessionRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;

    @Transactional
    public ChatSessionResponse createSession(Long userId, ChatSessionRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        ChatSessionEntity session = new ChatSessionEntity();
        session.setUserId(userId);
        session.setTitle(request.title() != null ? request.title() : "New Chat");

        ChatSessionEntity saved = chatSessionRepository.save(session);
        log.debug("Created chat session {} for user {}", saved.getSessionId(), userId);

        return toResponse(saved);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long sessionId, Long userId, ChatMessageRequest request) {
        ChatSessionEntity session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this session");
        }

        // 사용자 메시지 저장
        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setRole(ChatMessageEntity.MessageRole.USER);
        userMessage.setContent(request.message());
        chatMessageRepository.save(userMessage);

        // 세션 제목이 없거나 기본값이면 첫 메시지로 제목 설정
        if (session.getTitle() == null || session.getTitle().equals("New Chat")) {
            String title = request.message().length() > 50 
                    ? request.message().substring(0, 50) + "..."
                    : request.message();
            session.setTitle(title);
        }

        // 대화 기록 조회 (최근 20개 메시지로 제한)
        List<ChatMessageEntity> recentMessages = chatMessageRepository
                .findBySession_SessionIdOrderByCreatedAtAsc(sessionId);
        
        // Gemini API 호출을 위한 대화 기록 변환
        List<GeminiService.ChatMessage> conversationHistory = recentMessages.stream()
                .map(msg -> new GeminiService.ChatMessage(
                        msg.getRole().name(),
                        msg.getContent()
                ))
                .collect(Collectors.toList());

        // AI 응답 생성
        String aiResponse;
        try {
            aiResponse = geminiService.generateText(request.message(), conversationHistory);
        } catch (Exception e) {
            log.error("Failed to generate AI response", e);
            aiResponse = "죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }

        // AI 응답 저장
        ChatMessageEntity aiMessage = new ChatMessageEntity();
        aiMessage.setSession(session);
        aiMessage.setRole(ChatMessageEntity.MessageRole.ASSISTANT);
        aiMessage.setContent(aiResponse);
        chatMessageRepository.save(aiMessage);

        // 세션 업데이트 시간 갱신
        chatSessionRepository.save(session);

        log.debug("Sent message in session {}: user message saved, AI response generated", sessionId);

        return toMessageResponse(aiMessage);
    }

    public List<ChatSessionResponse> getUserSessions(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        List<ChatSessionEntity> sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return sessions.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ChatSessionResponse getSession(Long sessionId, Long userId) {
        ChatSessionEntity session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this session");
        }

        return toResponse(session);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        ChatSessionEntity session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this session");
        }

        chatSessionRepository.delete(session);
        log.debug("Deleted chat session {} for user {}", sessionId, userId);
    }

    private ChatSessionResponse toResponse(ChatSessionEntity session) {
        List<ChatMessageEntity> messages = chatMessageRepository
                .findBySession_SessionIdOrderByCreatedAtAsc(session.getSessionId());
        
        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        return new ChatSessionResponse(
                session.getSessionId(),
                session.getUserId(),
                session.getTitle(),
                messageResponses,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessageEntity message) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getRole().name(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}


