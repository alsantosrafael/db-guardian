-- Example SQL file with issues for testing

-- Issue 1: SELECT * (should trigger SELECT_STAR rule)
SELECT * FROM users;

-- Issue 2: Missing WHERE clause (should trigger MISSING_WHERE_CLAUSE rule)  
SELECT id, name, email FROM customers;

-- Issue 3: Cartesian join (should trigger CARTESIAN_JOIN rule)
SELECT u.name, p.title 
FROM users u, posts p;

-- Issue 4: Multiple issues in one query
SELECT * FROM orders o, products p;

-- Good query (should not trigger any rules)
SELECT id, name, email 
FROM users 
WHERE status = 'active' 
LIMIT 100;