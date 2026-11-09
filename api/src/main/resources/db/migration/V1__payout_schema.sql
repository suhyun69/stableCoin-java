CREATE TABLE payouts (
  id text PRIMARY KEY,
  idempotency_key text NOT NULL UNIQUE,
  merchant_id text NOT NULL,
  recipient_address text NOT NULL,
  amount_atomic numeric(78, 0) NOT NULL CHECK (amount_atomic > 0),
  request_fingerprint text NOT NULL,
  status text NOT NULL CHECK (status IN ('REQUESTED', 'AWAITING_CONFIRMATION', 'SETTLED', 'FAILED', 'REQUIRES_REVIEW')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE payout_status_history (
  id text PRIMARY KEY,
  payout_id text NOT NULL REFERENCES payouts(id),
  from_status text,
  to_status text NOT NULL,
  reason text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE outbox_messages (
  id text PRIMARY KEY,
  aggregate_type text NOT NULL,
  aggregate_id text NOT NULL REFERENCES payouts(id),
  type text NOT NULL,
  payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  attempts integer NOT NULL DEFAULT 0,
  available_at timestamptz NOT NULL DEFAULT now(),
  locked_at timestamptz,
  locked_by text,
  published_at timestamptz,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (aggregate_type, aggregate_id, type)
);
CREATE TABLE processed_messages (
  consumer_name text NOT NULL,
  message_id text NOT NULL,
  payout_id text NOT NULL REFERENCES payouts(id),
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer_name, message_id)
);
CREATE TABLE chain_transactions (
  id text PRIMARY KEY,
  payout_id text NOT NULL UNIQUE REFERENCES payouts(id),
  sender_address text NOT NULL,
  recipient_address text NOT NULL,
  amount_atomic numeric(78, 0) NOT NULL CHECK (amount_atomic > 0),
  nonce bigint NOT NULL,
  status text NOT NULL CHECK (status IN ('CREATED', 'SIGNED', 'BROADCAST_UNKNOWN', 'PENDING', 'MINED_SUCCESS', 'MINED_REVERTED', 'CONFIRMED', 'FAILED')),
  transaction_hash text UNIQUE,
  raw_transaction text,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  signed_at timestamptz,
  broadcast_at timestamptz,
  UNIQUE (sender_address, nonce)
);
CREATE TABLE chain_nonce_allocations (
  sender_address text PRIMARY KEY,
  next_nonce bigint NOT NULL CHECK (next_nonce >= 0)
);
ALTER TABLE chain_transactions
  ADD COLUMN included_block_number bigint,
  ADD COLUMN confirmations bigint NOT NULL DEFAULT 0,
  ADD COLUMN mined_at timestamptz,
  ADD COLUMN confirmed_at timestamptz;
ALTER TABLE payouts ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
CREATE TABLE reconciliation_findings (
  id text PRIMARY KEY,
  kind text NOT NULL,
  payout_id text REFERENCES payouts(id),
  transaction_hash text,
  log_index integer,
  detail jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'OPEN',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz,
  CONSTRAINT reconciliation_finding_status_check CHECK (status IN ('OPEN', 'RESOLVED'))
);
CREATE INDEX reconciliation_findings_open_idx ON reconciliation_findings (created_at) WHERE status = 'OPEN';
CREATE UNIQUE INDEX reconciliation_findings_open_payout_kind_idx ON reconciliation_findings (payout_id, kind) WHERE status = 'OPEN' AND payout_id IS NOT NULL;
CREATE UNIQUE INDEX reconciliation_findings_open_event_kind_idx ON reconciliation_findings (transaction_hash, log_index, kind) WHERE status = 'OPEN' AND transaction_hash IS NOT NULL AND log_index IS NOT NULL;
