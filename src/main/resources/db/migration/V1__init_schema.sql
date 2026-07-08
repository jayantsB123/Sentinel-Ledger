CREATE TABLE account (
                         id           UUID PRIMARY KEY,
                         account_no   VARCHAR(20) NOT NULL UNIQUE,
                         name         VARCHAR(255) NOT NULL,
                         balance      DECIMAL(19, 4) NOT NULL DEFAULT 0,
                         currency     VARCHAR(3) NOT NULL DEFAULT 'INR',
                         version      BIGINT NOT NULL DEFAULT 0,
                         created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transaction (
                             id                   UUID PRIMARY KEY,
                             idempotency_key      VARCHAR(255) NOT NULL UNIQUE,
                             request_fingerprint  VARCHAR(64) NOT NULL,
                             amount               DECIMAL(19, 4) NOT NULL,
                             currency             VARCHAR(3) NOT NULL DEFAULT 'INR',
                             status               VARCHAR(20) NOT NULL
                                 CHECK (status IN ('INITIATED', 'PROCESSING', 'COMPLETED', 'FAILED', 'TIMED_OUT')),
                             description          VARCHAR(255),
                             response_payload     JSONB,
                             created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
                             updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE entry (
                       id             UUID PRIMARY KEY,
                       amount         DECIMAL(19, 4) NOT NULL,
                       type           VARCHAR(10) NOT NULL CHECK (type IN ('DEBIT', 'CREDIT')),
                       account_id     UUID NOT NULL REFERENCES account(id),
                       transaction_id UUID NOT NULL REFERENCES transaction(id),
                       created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_idempotency_key ON transaction(idempotency_key);
CREATE INDEX idx_transaction_status ON transaction(status);
CREATE INDEX idx_entry_account_id ON entry(account_id);
CREATE INDEX idx_entry_transaction_id ON entry(transaction_id);