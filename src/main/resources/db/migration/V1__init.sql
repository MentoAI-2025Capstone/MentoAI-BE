CREATE TABLE "users" (
  "user_id" bigserial PRIMARY KEY,
  "auth_provider" auth_provider NOT NULL DEFAULT 'GOOGLE',
  "provider_user_id" text NOT NULL,
  "email" text NOT NULL,
  "name" text NOT NULL,
  "nickname" text,
  "profile_image_url" text,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "updated_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "user_profiles" (
  "user_id" bigint PRIMARY KEY,
  "birth_year" int,
  "university_name" text,
  "university_grade" int,
  "university_major" text,
  "updated_at" timestamptz
);

CREATE TABLE "user_profile_awards" (
  "award_id" bigserial PRIMARY KEY,
  "user_id" bigint,
  "title" text,
  "issuer" text,
  "date" date,
  "description" text,
  "url" text
);

CREATE TABLE "user_profile_certifications" (
  "cert_id" bigserial PRIMARY KEY,
  "user_id" bigint,
  "name" text,
  "issuer" text,
  "score_or_level" text,
  "issue_date" date,
  "expire_date" date,
  "credential_id" text,
  "url" text
);

CREATE TABLE "user_profile_experiences" (
  "exp_id" bigserial PRIMARY KEY,
  "user_id" bigint,
  "type" varchar,
  "title" text,
  "organization" text,
  "role" text,
  "start_date" date,
  "end_date" date,
  "is_current" boolean,
  "description" text,
  "url" text
);

CREATE TABLE "user_profile_experience_skills" (
  "exp_id" bigint,
  "skill_name" text
);

CREATE TABLE "user_profile_skills" (
  "user_id" bigint,
  "skill_name" text,
  "level" varchar
);

CREATE TABLE "tags" (
  "tag_id" bigserial PRIMARY KEY,
  "tag_name" text,
  "tag_type" varchar
);

CREATE TABLE "user_interests" (
  "user_id" bigint,
  "tag_id" bigint,
  "weight" double precision
);

CREATE TABLE "activities" (
  "activity_id" bigserial PRIMARY KEY,
  "title" text,
  "summary" text,
  "content" text,
  "type" varchar,
  "organizer" text,
  "location" text,
  "url" text,
  "is_campus" boolean,
  "status" varchar,
  "vector_doc_id" text,
  "published_at" timestamptz,
  "created_at" timestamptz,
  "updated_at" timestamptz
);

CREATE TABLE "activity_tags" (
  "activity_id" bigint,
  "tag_id" bigint
);

CREATE TABLE "activity_dates" (
  "date_id" bigserial PRIMARY KEY,
  "activity_id" bigint,
  "date_type" varchar,
  "date_value" timestamptz
);

CREATE TABLE "attachments" (
  "attachment_id" bigserial PRIMARY KEY,
  "activity_id" bigint,
  "file_type" varchar,
  "file_url" text,
  "ocr_text" text,
  "created_at" timestamptz
);

CREATE TABLE "refresh_tokens" (
  "refresh_token_id" bigserial PRIMARY KEY,
  "user_id" bigint NOT NULL,
  "token" text NOT NULL,
  "expires_at" timestamptz NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE "calendar_events" (
  "event_id" bigserial PRIMARY KEY,
  "user_id" bigint,
  "activity_id" bigint,
  "start_at" timestamptz,
  "end_at" timestamptz,
  "alert_minutes" int,
  "created_at" timestamptz
);

CREATE TABLE "target_roles" (
  "role_id" text PRIMARY KEY,
  "name" text,
  "expected_seniority" varchar,
  "created_at" timestamptz,
  "updated_at" timestamptz
);

CREATE TABLE "target_role_required_skills" (
  "role_id" text,
  "skill_name" text,
  "weight" double precision
);

CREATE TABLE "target_role_bonus_skills" (
  "role_id" text,
  "skill_name" text,
  "weight" double precision
);

CREATE TABLE "target_role_major_mapping" (
  "role_id" text,
  "major" text,
  "weight" double precision
);

CREATE TABLE "target_role_recommended_certs" (
  "role_id" text,
  "name" text
);

CREATE UNIQUE INDEX ON "user_profile_experience_skills" ("exp_id", "skill_name");

CREATE UNIQUE INDEX ON "user_profile_skills" ("user_id", "skill_name");

CREATE UNIQUE INDEX ON "user_interests" ("user_id", "tag_id");

CREATE UNIQUE INDEX ON "activity_tags" ("activity_id", "tag_id");

CREATE UNIQUE INDEX ON "target_role_required_skills" ("role_id", "skill_name");

CREATE UNIQUE INDEX ON "target_role_bonus_skills" ("role_id", "skill_name");

CREATE UNIQUE INDEX ON "target_role_major_mapping" ("role_id", "major");

CREATE UNIQUE INDEX ON "target_role_recommended_certs" ("role_id", "name");

CREATE UNIQUE INDEX "idx_users_email" ON "users" ("email");
CREATE UNIQUE INDEX "idx_users_provider" ON "users" ("auth_provider", "provider_user_id");
CREATE UNIQUE INDEX "idx_refresh_tokens_token" ON "refresh_tokens" ("token");

COMMENT ON TABLE "users" IS 'auth_provider: GOOGLE only (for now)';

COMMENT ON TABLE "tags" IS 'unique (tag_name, tag_type)';

ALTER TABLE "user_profiles" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");

ALTER TABLE "user_profile_awards" ADD FOREIGN KEY ("user_id") REFERENCES "user_profiles" ("user_id");

ALTER TABLE "user_profile_certifications" ADD FOREIGN KEY ("user_id") REFERENCES "user_profiles" ("user_id");

ALTER TABLE "user_profile_experiences" ADD FOREIGN KEY ("user_id") REFERENCES "user_profiles" ("user_id");

ALTER TABLE "user_profile_experience_skills" ADD FOREIGN KEY ("exp_id") REFERENCES "user_profile_experiences" ("exp_id");

ALTER TABLE "user_profile_skills" ADD FOREIGN KEY ("user_id") REFERENCES "user_profiles" ("user_id");

ALTER TABLE "user_interests" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");

ALTER TABLE "user_interests" ADD FOREIGN KEY ("tag_id") REFERENCES "tags" ("tag_id");

ALTER TABLE "activity_tags" ADD FOREIGN KEY ("activity_id") REFERENCES "activities" ("activity_id");

ALTER TABLE "activity_tags" ADD FOREIGN KEY ("tag_id") REFERENCES "tags" ("tag_id");

ALTER TABLE "activity_dates" ADD FOREIGN KEY ("activity_id") REFERENCES "activities" ("activity_id");

ALTER TABLE "attachments" ADD FOREIGN KEY ("activity_id") REFERENCES "activities" ("activity_id");

ALTER TABLE "refresh_tokens" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;

ALTER TABLE "calendar_events" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");

ALTER TABLE "calendar_events" ADD FOREIGN KEY ("activity_id") REFERENCES "activities" ("activity_id");

ALTER TABLE "target_role_required_skills" ADD FOREIGN KEY ("role_id") REFERENCES "target_roles" ("role_id");

ALTER TABLE "target_role_bonus_skills" ADD FOREIGN KEY ("role_id") REFERENCES "target_roles" ("role_id");

ALTER TABLE "target_role_major_mapping" ADD FOREIGN KEY ("role_id") REFERENCES "target_roles" ("role_id");

ALTER TABLE "target_role_recommended_certs" ADD FOREIGN KEY ("role_id") REFERENCES "target_roles" ("role_id");
