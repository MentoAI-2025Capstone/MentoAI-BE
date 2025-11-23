ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS target_role_id text;

ALTER TABLE user_profiles
    ADD CONSTRAINT fk_user_profiles_target_role
        FOREIGN KEY (target_role_id) REFERENCES target_roles (role_id);

CREATE INDEX IF NOT EXISTS idx_user_profiles_target_role
    ON user_profiles (target_role_id);


