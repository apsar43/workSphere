CREATE TABLE login_rate_limits (
    scope VARCHAR(20) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    failure_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_login_rate_limits PRIMARY KEY (scope, key_hash),
    CONSTRAINT ck_login_rate_limit_scope CHECK (scope IN ('ACCOUNT', 'SOURCE')),
    CONSTRAINT ck_login_rate_limit_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_login_rate_limits_cleanup
    ON login_rate_limits (updated_at);
