-- 채팅 세션 테이블 (사용자별 대화 세션)
CREATE TABLE "chat_sessions" (
  "session_id" bigserial PRIMARY KEY,
  "user_id" bigint NOT NULL,
  "title" text,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

-- 채팅 메시지 테이블 (대화 내용)
CREATE TABLE "chat_messages" (
  "message_id" bigserial PRIMARY KEY,
  "session_id" bigint NOT NULL,
  "role" varchar NOT NULL,  -- 'USER' or 'ASSISTANT'
  "content" text NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT now()
);

-- 인덱스
CREATE INDEX "idx_chat_sessions_user_id" ON "chat_sessions" ("user_id");
CREATE INDEX "idx_chat_messages_session_id" ON "chat_messages" ("session_id");
CREATE INDEX "idx_chat_messages_created_at" ON "chat_messages" ("created_at");

-- 외래키
ALTER TABLE "chat_sessions" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;
ALTER TABLE "chat_messages" ADD FOREIGN KEY ("session_id") REFERENCES "chat_sessions" ("session_id") ON DELETE CASCADE;


