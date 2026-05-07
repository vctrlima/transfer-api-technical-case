INSERT INTO accounts (id, status, balance, version, created_at, updated_at) VALUES
    ('acc-origin-001', 'ACTIVE', 1500.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('acc-dest-001',   'ACTIVE', 500.00,  0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('acc-inactive-001', 'INACTIVE', 200.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('acc-blocked-001',  'BLOCKED',  300.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
