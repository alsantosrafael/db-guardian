-- Migration file with more issues

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255)
);

CREATE TABLE profiles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    bio TEXT
);

-- Problematic query in migration
SELECT * FROM users WHERE 1=1;

-- Another bad query
SELECT u.*, p.* FROM users u, profiles p;