CREATE TABLE fetcher_settings (
    id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    interval_minutes INT NOT NULL DEFAULT 60,
    last_run_at TIMESTAMPTZ,
    last_summary VARCHAR(500),
    last_error VARCHAR(500)
);

INSERT INTO fetcher_settings (id, enabled, interval_minutes)
VALUES (1, FALSE, 60);
