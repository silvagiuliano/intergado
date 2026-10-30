PRAGMA journal_mode=WAL;

CREATE TABLE IF NOT EXISTS purchase_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL CHECK (length(title) <= 160),
  farm TEXT NOT NULL CHECK (length(farm) <= 100),
  category TEXT NOT NULL CHECK (length(category) <= 80),
  requester TEXT NOT NULL CHECK (length(requester) <= 100),
  supplier TEXT CHECK (supplier IS NULL OR length(supplier) <= 120),
  amount NUMERIC NOT NULL DEFAULT 0 CHECK (amount >= 0),
  status TEXT NOT NULL CHECK (status IN ('RECEIVED','QUOTING','AWAITING_APPROVAL','APPROVED','ORDERED','DELIVERED','PAID')),
  requested_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  demo INTEGER NOT NULL DEFAULT 0 CHECK (demo IN (0,1))
);

CREATE INDEX IF NOT EXISTS idx_purchase_requests_status ON purchase_requests(status);
CREATE INDEX IF NOT EXISTS idx_purchase_requests_requested_at ON purchase_requests(requested_at DESC);

CREATE TABLE IF NOT EXISTS farms (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE CHECK (length(name) <= 100),
  balance_date TEXT NOT NULL,
  herd_balance INTEGER NOT NULL CHECK (herd_balance >= 0),
  adepara_balance INTEGER NOT NULL CHECK (adepara_balance >= 0),
  sources TEXT NOT NULL DEFAULT '',
  demo INTEGER NOT NULL DEFAULT 0 CHECK (demo IN (0,1))
);
