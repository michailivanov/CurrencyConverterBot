CREATE TABLE IF NOT EXISTS currency (
    id SERIAL PRIMARY KEY,
    name VARCHAR(3) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    default_pair_from_id INTEGER NOT NULL,
    default_pair_to_id INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (default_pair_from_id) REFERENCES currency (id),
    FOREIGN KEY (default_pair_to_id) REFERENCES currency (id)
);

CREATE TABLE IF NOT EXISTS log_in_out (
    id SERIAL PRIMARY KEY,
    is_in BOOLEAN NOT NULL,
    tg_username VARCHAR(255) NOT NULL,
    user_id INTEGER NOT NULL,
    time_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS conversion_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    from_currency_id INTEGER NOT NULL,
    to_currency_id INTEGER NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    rate DECIMAL(17, 8) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (from_currency_id) REFERENCES currency (id),
    FOREIGN KEY (to_currency_id) REFERENCES currency (id)
);