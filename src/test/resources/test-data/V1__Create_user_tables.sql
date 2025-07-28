-- Migration file with various SQL patterns
-- V1__Create_user_tables.sql

-- Good CREATE statements
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    total DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending'
);

-- Bad queries that should be flagged
SELECT * FROM users;

UPDATE users SET active = false;

DELETE FROM orders;

-- Good queries  
SELECT id, name, email FROM users WHERE active = true;

UPDATE users SET last_login = NOW() WHERE id = '123e4567-e89b-12d3-a456-426614174000';

DELETE FROM orders WHERE status = 'cancelled' AND created_at < NOW() - INTERVAL '30 days';