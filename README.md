# Sentinel-Ledger

*A payment gateway that refuses to lie to you.*

Sentinel-Ledger is a from-scratch implementation of the hardest problem in payments engineering: guaranteeing that a transaction happens exactly once, even when clients retry, networks flake, threads race, and servers crash mid-transfer. It is not a CRUD app with a payments table bolted on - it's an exercise in building the kind of correctness guarantees that real money-movement systems (UPI, card networks, wire transfers) are built around.

If you've ever wondered what actually stops your bank from double-charging you when your app hangs and you tap "Pay" twice - this repo is that mechanism, built from the ground up.

## Why this exists

Most portfolio payment projects stop at "here's an API that moves money between two rows." That's the easy 20%. The other 80% - the part that actually matters in production - is:

- What happens when the same request arrives twice, milliseconds apart, on two different threads?
- What happens when a client retries after a timeout, not knowing if the original request actually succeeded?
- What happens when your server crashes between debiting one account and crediting the other?
- What happens when two transfers race for the same pair of accounts in opposite directions?
- How do you prove, months later, that every rupee that moved is accounted for?

Sentinel-Ledger answers each of these with a specific, deliberate mechanism - not a TODO comment.

## Architecture at a glance

```
Client Request
|
|
|
v
+-------------------+
|  Rate Limiter      |  Redis Token Bucket (Lua, atomic) - per-client throttling
+---------+-----------+
|
v
+-------------------+
|  Idempotency       |  3-layer defense (below) - is this a duplicate?
|  Orchestration     |
+---------+-----------+
|
v
+-------------------+
|  Audit Record       |  REQUIRES_NEW commit - logged before anything risky runs
+---------+-----------+
|
v
+-------------------+
|  Money Movement     |  Deterministic account locking -> balance update ->
|  (Transactional)    |  double-entry write, all-or-nothing
+---------------------+
```

## The core engineering decisions

### 1. Idempotency is a three-layer system, not a database column

A single `UNIQUE` constraint on an idempotency key is the textbook answer - and it's not enough on its own. Sentinel-Ledger layers three defenses, each catching what the layer above it misses:

| Layer | Catches | Speed |
|---|---|---|
| Redis cache-aside (24h TTL) | Client retries after original request completed | Sub-millisecond, no DB hit |
| Redis distributed lock (Lua-scripted safe unlock) | Two requests with the same key arriving simultaneously on different threads | Millisecond-scale |
| Postgres `UNIQUE` constraint | Everything else - Redis restarts, cache eviction, split-brain scenarios | The unbreakable backstop |

The lock uses a Lua script for unlock, not a plain `DEL` - because a naive delete can release a lock that no longer belongs to you (if your own lock already expired under TTL and someone else acquired it in the meantime). The script only deletes if the stored token still matches the caller's, making check-and-delete atomic.

Every request is also fingerprinted (SHA-256 over the meaningful fields) so that the same key with a different payload is rejected outright - protecting against both client bugs and replay tampering, not just legitimate retries.

### 2. The audit trail is structurally guaranteed, not best-effort

Early in development, failed transactions simply weren't showing up in the database - a subtle but classic Spring pitfall: self-invocation silently defeats `@Transactional`. Calling a `@Transactional` method from within the same class bypasses Spring's proxy entirely, and the annotation is ignored with no warning. Combined with rolling back an already-flushed row on exception, failed payments were vanishing instead of being recorded - which is exactly backwards for a system whose entire value proposition is auditability.

The fix: transaction logging is split into its own independently-committing step. `TransactionAuditService.createProcessingRecord()` runs in `REQUIRES_NEW` and commits before any risky business logic executes. Whatever happens next - success, insufficient funds, account not found, an unexpected exception - the fact that the attempt happened is already durable. Every transaction that reaches the execution layer is logged, unconditionally, regardless of outcome.

### 3. Money movement is one atomic unit, with deadlock-safe locking

Debiting one account and crediting another has to be all-or-nothing - a partial write is a corrupted ledger. This runs inside a single `@Transactional` boundary using `SELECT ... FOR UPDATE` (pessimistic locking) on both accounts.

The subtle bug this avoids: if Transaction A locks `(account1 -> account2)` while Transaction B simultaneously locks `(account2 -> account1)`, they deadlock waiting on each other forever. Sentinel-Ledger always acquires locks in a deterministic order (by UUID comparison), regardless of which account is the sender - eliminating the deadlock class entirely rather than handling it after the fact.

### 4. Double-entry bookkeeping, not a balance field you just trust

Every completed transfer produces exactly two `BankEntry` rows - one `DEBIT`, one `CREDIT` - linked to the parent `BankTransaction`. This is the same principle every real accounting system is built on: a balance is never just a number you mutate and hope is right, it's the sum of an immutable, append-only trail of entries. Reconciliation becomes a query, not an act of faith.

### 5. Rate limiting via atomic token bucket, not naive counters

`INCR` + `EXPIRE` as two separate Redis calls creates a race - two concurrent requests can both read "under the limit" before either writes back. The token bucket here runs as a single Lua script: read current tokens, compute lazy refill based on elapsed time, decide allow/deny, write back - all as one indivisible Redis operation. No background job is needed to "top up" buckets; refill is computed on-demand, so idle keys cost nothing.

Buckets are keyed by client identity (`X-Client-Id` header, falling back to IP) - deliberately not by idempotency key, since an attacker could otherwise sidestep the limiter by simply generating a new key per request.

## Tech stack

| Layer | Choice |
|---|---|
| Language / Framework | Java 23, Spring Boot |
| Database | PostgreSQL, schema-versioned via Flyway |
| Cache / Coordination | Redis (Lettuce client), Lua scripting for atomicity |
| Persistence | Spring Data JPA / Hibernate |
| DB tooling | DBeaver |

## What's implemented

- **Idempotent payment API** - `POST /api/v1/payments`, header-based idempotency key, full 3-layer duplicate protection
- **Double-entry ledger** - `BankAccount`, `BankTransaction`, `BankEntry` with enforced DEBIT/CREDIT pairing
- **Deterministic concurrency control** - pessimistic account locking with deadlock-safe lock ordering
- **Distributed locking** - Redis + Lua, safe against lock-token mismatch
- **Request fingerprinting** - SHA-256, rejects idempotency-key reuse with mismatched payloads
- **Unconditional audit logging** - every transaction attempt persisted regardless of outcome, via isolated `REQUIRES_NEW` transactions
- **Rate limiting** - Redis token bucket, atomic Lua execution, per-client throttling, 429 + `Retry-After` semantics
- **Structured error handling** - dedicated exception types per failure mode (insufficient funds, concurrent request, idempotency conflict, rate limit exceeded), mapped to correct HTTP status codes with no internal detail leakage

## Known gaps (deliberately deferred, not overlooked)

- **Stale transaction sweeper** - a transaction crashed mid-`PROCESSING` currently has no automatic resolution path; a reconciliation job to detect and resolve these is the next piece of work.
- **Circuit breaker** - intentionally not built yet, since there's no external downstream dependency (bank/PSP call) to protect against. Premature before that integration exists.
- **Outbox pattern / event publishing** - no Kafka producer yet; relevant once other services need to react to completed payments.
- **Observability** - no Micrometer/Prometheus metrics or structured tracing wired up yet.
- **OpenAPI / Swagger** - no interactive API docs yet.
- **Containerization & Kubernetes** - app itself isn't containerized yet (dependencies are, via Docker).
- **Load testing** - the concurrency guarantees above are architecturally sound but not yet proven under simulated load; this is the next highest-value addition to make the correctness claims verifiable rather than theoretical.

## Getting started

```bash
# 1. Start dependencies
docker run --name sentinel-ledger-pg -e POSTGRES_USER=sentinel \
-e POSTGRES_PASSWORD=sentinel123 -e POSTGRES_DB=sentinel_ledger \
-p 5432:5432 -d postgres:16

docker run --name sentinel-ledger-redis -p 6379:6379 -d redis:7-alpine

# 2. Run the app (Flyway migrates schema automatically on boot)
./gradlew bootRun
```

## Try it

```bash
curl -X POST http://localhost:8080/api/v1/payments \
-H "Content-Type: application/json" \
-H "Idempotency-Key: $(uuidgen)" \
-d '{
"fromAccountId": "<uuid>",
"toAccountId": "<uuid>",
"amount": 500.00,
"currency": "INR",
"description": "test transfer"
}'
```

Fire the exact same request again with the same `Idempotency-Key` - the balance won't move twice.

## A note on scope

This project is deliberately narrow and deep rather than broad and shallow. It would have been faster to bolt together a dozen half-implemented features. Instead, every piece marked done above has been built, broken, debugged, and fixed against a real race condition or a real rollback edge case - not just written and assumed correct. The gaps section exists because it's more useful to state what's genuinely missing than to let a checklist imply more than what's actually been proven.