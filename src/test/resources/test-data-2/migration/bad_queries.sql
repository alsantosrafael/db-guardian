-- Collection of problematic SQL queries for testing

-- BAD: SELECT * queries
SELECT * FROM products;
SELECT * FROM categories WHERE parent_id IS NULL;
SELECT * FROM product_reviews WHERE rating >= 4;

-- BAD: Leading wildcard LIKE queries
SELECT * FROM products WHERE name LIKE '%phone%';
SELECT * FROM categories WHERE name LIKE '%electronics%';

-- BAD: Missing WHERE clause in UPDATE/DELETE
UPDATE products SET updated_at = NOW();
DELETE FROM product_reviews;
UPDATE categories SET name = UPPER(name);

-- BAD: Cartesian products
SELECT * FROM products p, categories c WHERE p.price > 100;
SELECT * FROM products, product_reviews WHERE rating > 3;

-- BAD: Non-SARGable WHERE clauses
SELECT * FROM products WHERE UPPER(name) = 'LAPTOP';
SELECT * FROM product_reviews WHERE DATE(created_at) = CURRENT_DATE;
SELECT * FROM products WHERE SUBSTRING(sku, 1, 3) = 'ABC';

-- BAD: Complex subqueries in SELECT
SELECT *,
       (SELECT COUNT(*) FROM product_reviews r WHERE r.product_id = p.id) as review_count,
       (SELECT AVG(rating) FROM product_reviews r2 WHERE r2.product_id = p.id) as avg_rating,
       (SELECT MAX(created_at) FROM product_reviews r3 WHERE r3.product_id = p.id) as latest_review
FROM products p;

-- BAD: No LIMIT on large result sets
SELECT * FROM product_reviews ORDER BY created_at DESC;
SELECT * FROM products ORDER BY price DESC;

-- BAD: Inefficient EXISTS queries
SELECT * FROM products p 
WHERE EXISTS (
    SELECT 1 FROM product_reviews r 
    WHERE r.product_id = p.id 
    AND EXISTS (
        SELECT 1 FROM tags t 
        WHERE t.name LIKE '%popular%'
    )
);

-- BAD: Multiple OR conditions instead of IN
SELECT * FROM products 
WHERE category_id = 1 
   OR category_id = 2 
   OR category_id = 3 
   OR category_id = 4 
   OR category_id = 5;

-- BAD: DISTINCT without proper analysis
SELECT DISTINCT * FROM products p, categories c WHERE p.category_id = c.id;

-- BAD: Functions in ORDER BY preventing index usage
SELECT * FROM products ORDER BY UPPER(name);
SELECT * FROM product_reviews ORDER BY EXTRACT(MONTH FROM created_at);

-- GOOD examples for comparison:
-- SELECT id, name, price FROM products WHERE category_id = 1;
-- SELECT p.id, p.name, c.name FROM products p JOIN categories c ON p.category_id = c.id;
-- UPDATE products SET updated_at = NOW() WHERE id = 123;