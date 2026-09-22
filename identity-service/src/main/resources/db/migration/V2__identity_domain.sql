CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
 username VARCHAR(50) UNIQUE, full_name VARCHAR(255),
 is_active BOOLEAN NOT NULL DEFAULT TRUE, deleted_at TIMESTAMP WITH TIME ZONE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK (email = LOWER(TRIM(email))),
 CHECK (username IS NULL OR username = LOWER(TRIM(username)))
);
CREATE TABLE user_credentials (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 password_hash TEXT NOT NULL, password_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE roles (id SMALLINT PRIMARY KEY, code VARCHAR(32) NOT NULL UNIQUE);
INSERT INTO roles VALUES (1, 'USER'), (2, 'ADMIN');
CREATE TABLE user_roles (
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 role_id SMALLINT NOT NULL REFERENCES roles(id),
 PRIMARY KEY(user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles(role_id, user_id);
CREATE TABLE auth_sessions (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK (expires_at > created_at)
);
CREATE INDEX ix_sessions_user ON auth_sessions(user_id, created_at);
CREATE TABLE refresh_tokens (
 id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, used_at TIMESTAMP WITH TIME ZONE,
 revoked_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK (expires_at > created_at)
);
CREATE INDEX ix_refresh_session ON refresh_tokens(session_id);
CREATE INDEX ix_refresh_expiry ON refresh_tokens(expires_at);
CREATE TABLE outbox_events (
 event_id UUID PRIMARY KEY, aggregate_id UUID NOT NULL, aggregate_version BIGINT NOT NULL,
 event_type VARCHAR(100) NOT NULL, schema_version INTEGER NOT NULL DEFAULT 1,
 payload TEXT NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_outbox_pending ON outbox_events(published_at, occurred_at);
