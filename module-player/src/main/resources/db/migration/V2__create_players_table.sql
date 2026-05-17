CREATE TABLE IF NOT EXISTS players (
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,
    first_name     VARCHAR(255) NOT NULL,
    last_name      VARCHAR(255) NOT NULL,
    position       VARCHAR(255) NOT NULL,
    alter_position VARCHAR(255) NOT NULL,
    team_id        BIGINT REFERENCES teams(id)
);

