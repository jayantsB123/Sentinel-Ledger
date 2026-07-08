-- Accounts
INSERT INTO account (id, account_no, name, balance, currency) VALUES
                                                                  ('11111111-1111-1111-1111-111111111111', 'ACC1001', 'Jayant Deshmukh', 50000.0000, 'INR'),
                                                                  ('22222222-2222-2222-2222-222222222222', 'ACC1002', 'Riya Sharma',    120000.0000, 'INR'),
                                                                  ('33333333-3333-3333-3333-333333333333', 'ACC1003', 'Merchant Store', 5000.0000, 'INR');

-- Transaction 1: Completed transfer, Jayant -> Riya, 1500.00
INSERT INTO transaction (id, idempotency_key, request_fingerprint, amount, currency, status, description) VALUES
    ('aaaaaaaa-0001-0001-0001-000000000001', 'idem-key-001', 'fp-hash-001', 1500.0000, 'INR', 'COMPLETED', 'Rent payment');

INSERT INTO entry (id, amount, type, account_id, transaction_id) VALUES
                                                                     ('eeeeeeee-0001-0001-0001-000000000001', 1500.0000, 'DEBIT',  '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0001-0001-0001-000000000001'),
                                                                     ('eeeeeeee-0001-0001-0001-000000000002', 1500.0000, 'CREDIT', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-0001-0001-0001-000000000001');

-- Transaction 2: Stuck mid-flight (simulates crash before completion) : for testing idempotent retry
INSERT INTO transaction (id, idempotency_key, request_fingerprint, amount, currency, status, description) VALUES
    ('aaaaaaaa-0002-0002-0002-000000000002', 'idem-key-002', 'fp-hash-002', 750.0000, 'INR', 'PROCESSING', 'Grocery payment');

-- Transaction 3: Failed (e.g. insufficient funds downstream)
INSERT INTO transaction (id, idempotency_key, request_fingerprint, amount, currency, status, description) VALUES
    ('aaaaaaaa-0003-0003-0003-000000000003', 'idem-key-003', 'fp-hash-003', 200000.0000, 'INR', 'FAILED', 'Large transfer attempt');

-- Transaction 4: Completed, merchant payment, small amount
INSERT INTO transaction (id, idempotency_key, request_fingerprint, amount, currency, status, description) VALUES
    ('aaaaaaaa-0004-0004-0004-000000000004', 'idem-key-004', 'fp-hash-004', 299.0000, 'INR', 'COMPLETED', 'Store purchase');

INSERT INTO entry (id, amount, type, account_id, transaction_id) VALUES
                                                                     ('eeeeeeee-0004-0004-0004-000000000001', 299.0000, 'DEBIT',  '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0004-0004-0004-000000000004'),
                                                                     ('eeeeeeee-0004-0004-0004-000000000002', 299.0000, 'CREDIT', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-0004-0004-0004-000000000004');