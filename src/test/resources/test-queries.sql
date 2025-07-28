-- Test SQL file for smoke testing
-- This file contains various SQL patterns that should trigger different analysis rules

-- SELECT * pattern (should trigger AVOID_SELECT_STAR)
SELECT * FROM users WHERE active = true;

-- Missing WHERE clause (should trigger REQUIRE_WHERE_CLAUSE)
UPDATE users SET last_login = NOW();
DELETE FROM user_sessions;

-- Potential cartesian join (should trigger PREVENT_CARTESIAN_JOINS)
SELECT u.name, o.total 
FROM users u, orders o 
WHERE u.active = true;

-- Large OFFSET (should trigger OPTIMIZE_PAGINATION)
SELECT id, name FROM products 
ORDER BY created_at 
LIMIT 20 OFFSET 10000;

-- Subquery that could be a JOIN (should trigger OPTIMIZE_SUBQUERIES)
SELECT * FROM orders 
WHERE customer_id IN (
    SELECT id FROM customers WHERE country = 'US'
);

-- UNION without ALL (should trigger OPTIMIZE_UNION_OPERATIONS)
SELECT name FROM employees 
UNION 
SELECT name FROM contractors;

-- Good query (should not trigger issues)
SELECT id, name, email 
FROM users 
WHERE created_at > '2024-01-01' 
  AND active = true 
ORDER BY created_at DESC 
LIMIT 50;