-- GOOD: Proper table creation with indexes
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    parent_id BIGINT REFERENCES categories(id)
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_name ON categories(name);

-- GOOD: Well-structured table with proper constraints
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(50) NOT NULL UNIQUE,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    description TEXT,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_price ON products(price);

-- GOOD: Junction table with composite primary key
CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE product_tags (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, tag_id)
);

-- BAD: Table without proper indexes
CREATE TABLE bad_entities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    parent_id BIGINT REFERENCES bad_entities(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- BAD: Missing foreign key constraints
CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT,
    reviewer_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- GOOD: Add foreign key constraint later
ALTER TABLE product_reviews 
ADD CONSTRAINT fk_product_reviews_product 
FOREIGN KEY (product_id) REFERENCES products(id);

-- GOOD: Add indexes for common queries
CREATE INDEX idx_product_reviews_product ON product_reviews(product_id);
CREATE INDEX idx_product_reviews_rating ON product_reviews(rating);
CREATE INDEX idx_product_reviews_created ON product_reviews(created_at);

-- BAD: Missing indexes on frequently queried columns
CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- GOOD: Partial index for active sessions only
CREATE INDEX idx_user_sessions_active 
ON user_sessions(user_id, expires_at) 
WHERE expires_at > NOW();