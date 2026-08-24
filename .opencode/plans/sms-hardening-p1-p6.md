# SMS Hardening Phase — Implementation Plan · REVISION 3 (decision-complete, approved)

Status: APPROVED for implementation. Rev2 incorporated mandatory changes #6–#10. Rev3 adds INV-6 bounded-recovery policy + postedAt semantics clarification. Execution awaits edit-permission grant (`edit *` currently denied; only this plans dir writable). Apply top-to-bottom.

## Locked decisions (unchanged)
1. Catch-Up posts the same Stage-1 notification when it wins the claim.
2. Balance-discrepancy entries = single net-amount INFERRED pending, "≈ ₹X unlogged activity [Unverified]" UI, never rendered confirmed.
3. Provenance columns on BOTH tables (schema v8).
4. Full P1–P6 scope.

## Invariants (NEW — binding on all code in this phase)
- INV-1 **HashCache is a performance cache, never an authority.** `peek()==true` ⇒ safe skip. `peek()==false` ⇒ MUST fall through to the Room atomic claim. Cache loss/clearing must never cause duplicates or lost messages. (Interface renamed from HashStore → **SmsHashCache**; backing prefs file `"sms_processed_hashes"` unchanged.)
- INV-2 **Room is the sole dedup authority** via `UNIQUE(sourceMessageHash)` + `OnConflictStrategy.IGNORE` returning `-1`.
- INV-3 **Every multi-row identity transition is atomic** (single Room transaction): (a) claim→history-check→discard, (b) auto-merge consume+insert, (c) notification-action log, (d) dashboard approve. Notifications fire only AFTER commit.
- INV-4 **Exactly-once PROCESSING; notification ATTEMPT semantics (bounded).** Guarantee statement used everywhere (docs/UI copy/tests): "Every valid, linked bank SMS that Android delivers produces exactly one processed identity. Each unconfirmed pending is retried for its Stage-1 notification within the INV-6 window/attempt bounds; visual delivery remains subject to POST_NOTIFICATIONS grant and channel enabledness."
- INV-5 Technical capability ≠ Play distribution eligibility. `RECEIVE_SMS`/`READ_SMS` are hard-restricted dangerous permissions; docs/HARDENING_CHECKLIST.md must carry the Play Console policy-separation note verbatim.
- INV-6 **Bounded notification recovery.** Constants in `TransactionProcessor` companion: `NOTIFICATION_RECOVERY_WINDOW_MS = 48L * 60 * 60 * 1000` (48h), `MAX_NOTIFICATION_ATTEMPTS = 5`. `recoverUnnotified()` selects rows where `notificationPostedAt IS NULL AND confidence='CONFIRMED' AND source!='BALANCE_RECONCILIATION' AND timestamp > now−WINDOW AND notificationAttempts < MAX`. After either limit is reached the pending transaction remains fully valid and visible in the dashboard card for manual action, but automatic notification retry stops permanently (no pathological loops, no months-later popups).
- SEMANTIC NOTE (binding): `notificationPostedAt != null` means ONLY "notifier.post() returned without throwing". It must NEVER be interpreted as user-seen/read. State ladder NOT_ATTEMPTED → POSTED → USER_INTERACTED is deliberately out of scope for this phase; USER_INTERACTED is currently implied by pending-row deletion via action/dismiss/approve paths.

---

## P1 · Schema v7→v8 + identity model

New files:
- `data/model/TransactionSource.kt`: `enum TransactionSource { SMS_REALTIME, SMS_CATCHUP, BALANCE_RECONCILIATION, NOTIFICATION_ACTION, MANUAL }`
- `data/model/TransactionConfidence.kt`: `enum TransactionConfidence { CONFIRMED, INFERRED, MANUAL }`

Entities:
- `PendingTransactionEntity`: add
  - `sourceMessageHash: String? = null` (+ `Index(value=["sourceMessageHash"], unique=true)` → canonical name `index_pending_transactions_sourceMessageHash`)
  - `source: String? = null`, `confidence: String? = null`
  - `notificationPostedAt: Long? = null`
  - `@ColumnInfo(defaultValue = "0") val notificationAttempts: Int = 0` (annotation REQUIRED to match migration default)
- `TransactionEntity`: add `sourceMessageHash/source/confidence` nullable + unique index `index_transactions_sourceMessageHash`; existing FK/indices untouched.

`HisabDatabase.kt`: `version = 8`;
```sql
ALTER TABLE pending_transactions ADD COLUMN sourceMessageHash TEXT DEFAULT NULL;
ALTER TABLE pending_transactions ADD COLUMN source TEXT DEFAULT NULL;
ALTER TABLE pending_transactions ADD COLUMN confidence TEXT DEFAULT NULL;
ALTER TABLE pending_transactions ADD COLUMN notificationPostedAt INTEGER DEFAULT NULL;
ALTER TABLE pending_transactions ADD COLUMN notificationAttempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN sourceMessageHash TEXT DEFAULT NULL;
ALTER TABLE transactions ADD COLUMN source TEXT DEFAULT NULL;
ALTER TABLE transactions ADD COLUMN confidence TEXT DEFAULT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS index_pending_transactions_sourceMessageHash ON pending_transactions(sourceMessageHash);
CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourceMessageHash ON transactions(sourceMessageHash);
```
Register `MIGRATION_7_8` in `addMigrations(...)`.

`AutoBackupManager`: BACKUP_VERSION→6; serialize emits all new fields incl. notificationPostedAt/attempts; restore reads tolerant (`opt*`); fingerprint logic unchanged.

## P2 · TransactionProcessor (JVM-pure) — REVISED

Gateways (same file, zero Android imports):
```kotlin
interface SmsHashCache { fun peek(key: String): Boolean; fun mark(vararg keys: String); fun forget(key: String) }
data class SmsClock(val nowMillis: () -> Long, val today: () -> LocalDate)
fun interface AtomicDb { suspend fun <T> inTransaction(block: suspend () -> T): T }   // Android impl: room.withTransaction; JVM fake: direct invoke
interface SmsNotifier { suspend fun postBankTransaction(pending): Boolean; fun postMissed(...): Boolean; fun postAutoMerge(...) }
   // Boolean = "post() returned without throwing" → drives markNotified
fun interface BackupTrigger { suspend fun perform() }
enum class SmsOrigin { REALTIME, CATCHUP }
sealed class ProcessResult { PendingCreated | AutoMerged(txId,from,to,amount) | Duplicate | Suppressed(reason) /* PARSE_FAILED,NO_BANK,OTP_NOISE,BLACKLISTED,NO_AMOUNT,NO_DIRECTION,NOT_LINKED,MANUAL_RECON,RECON_HASH,BALANCE_MATCHED,MERGE_LOST_RACE */ }
```

Stage order (parity unless noted):
1. Parse gates → Suppressed.
2. Account gate (parity `SmsReceiver.kt:61-77`; zero-linked-accounts ⇒ proceed).
3. INV-1 cache peek ⇒ Duplicate.
4. Manual-recon (read-only, ±₹1/24h/account-aware) ⇒ Suppressed(MANUAL_RECON).
5. Recon-key `"recon_${amount}_${type}"` consume ⇒ Suppressed(RECON_HASH).
6. CATCHUP-only proximity tiers (±30min pending/txn; |3-day-net − ending|<1.0) ⇒ Suppressed(BALANCE_MATCHED).
7. **Atomic claim block** `atomicDb.inTransaction {`:
   a. `insertClaim(pending.copy(hash=canonical, source=origin, confidence=CONFIRMED, notificationPostedAt=null))` (IGNORE) → `-1` ⇒ Duplicate;
   b. `transactionDao.getBySourceHash(hash)` non-null ⇒ delete claimed row ⇒ Duplicate (closes cross-table hole);
   c. return claimedId } — crash between (a) and outside-commit impossible mid-block; post-commit crash leaves claimed-but-unnotified pending → recovered by step 11.
8. **Auto-merge** entirely inside one `inTransaction`: opposite-pending lookup (120s, different bank ignoreCase); `deleteById(opposite)==0` ⇒ MERGE_LOST_RACE (delete own claimed row, exit); insert TRANSFER txn carrying canonical hash + origin + CONFIRMED; delete OWN claimed pending; commit. THEN notify(postAutoMerge) → backupTrigger → return AutoMerged.
9. **Balance sync** under companion Mutex + monotonic guard (`smsTimestampMs <= lastBalanceTimestamp && !=null` ⇒ skip overwrite); discrepancy ≥₹1.0 ⇒ INFERRED pending (hash=null, source=BALANCE_RECONCILIATION, confidence=INFERRED, marker merchant string preserved, dedupe via findInferredMarker) + postMissed → markNotified(inferredId) on success; ALWAYS update lastKnownBalance/lastBalanceTimestamp both origins.
10. Return PendingCreated; ADAPTER posts Stage-1 iff shouldNotify && permission; on success `markNotified(claimedId)`.
11. **Recovery API (NEW)**: `suspend fun recoverUnnotified(): List<Long>` — selects pendings per INV-6 (`notificationPostedAt IS NULL AND confidence='CONFIRMED' AND source!='BALANCE_RECONCILIATION' AND timestamp > now−48h AND notificationAttempts < MAX_NOTIFICATION_ATTEMPTS`), re-posts appropriate notification (deterministic id `id*1000` makes retries replace-not-duplicate), marks on success. Invoked from **MainActivity LaunchedEffect** (NOT catch-up: recovery must not require READ_SMS).
12. `markNotified(id)` = single UPDATE `notificationPostedAt=now, notificationAttempts=notificationAttempts+1`; failed attempt increments attempts only (drives the INV-6 cap).

DAO deltas vs Rev1: PendingTransactionDao gains `insertClaim` (IGNORE→Long), `deleteById→Int`, `getBySourceHash`, `findInferredMarker(amount,last4)`, `getUnnotified(cutoff): List`, `markNotified(id, ts)`; TransactionDao gains `getBySourceHash`.

Android wiring `data/sms/AndroidSmsGateways.kt`: SharedPreferences-backed SmsHashCache (INV-1 doc-comment verbatim), RoomAtomicDb(db){ withTransaction }, RealSmsNotifier(permission-gated), AutoBackupTrigger.

## P3 · Thin adapters
- SmsReceiver: PDU concat + `internal suspend fun handleSms(sender,body,ts)` seam; goAsync; finally finish(). All gates/hashes deleted from file.
- SmsCatchUpSync: READ_SMS guard + 24h cursor loop → process(CATCHUP, smsDate); logs outcomes; no notification code left (processor/adapters handle).
- Dashboard approve + NotificationActionReceiver paths wrapped in `db.withTransaction` (INV-3 c/d).

## P4 · Correctness fixes (unchanged from Rev1)
BankAliasRegistry.matches in action receiver; repo-level MANUAL/MANUAL default tagging; approve carries hash/source + confidence=MANUAL; INFERRED card copy + Unverified chip.

## P5 · Hygiene (unchanged)
Remove dead MANAGE_APP_ALL_FILES flow; PackageManager-driven version strings in SettingsScreen; drop unused CategoryIconMapper imports ×3.

## P6 · Proof (~50 tests target)
Rev1 matrix retained (BOB trio ×3, dual-origin both orders, same-amount-twice, manual-recon, recon-key, auto-merge happy+lost-race, balance 25→miss10→debit10, stale-monotonic, net-math 40−25−10, catch-up tiers; legacy 11 untouched-green).
NEW (mandatory #10):
- T-CRASH-1: simulate crash-after-claim (row with postedAt=null) → recoverUnnotified() posts once, marks; second run ⇒ no repost.
- T-CRASH-2: crash-between-claim-and-history-check impossible-by-construction test: pre-existing txn with same hash ⇒ claim discarded (Duplicate).
- T-CACHE-1: clear cache between deliveries ⇒ Room claim still suppresses (INV-1).
- T-XREF-1: after approve/merge/action-log paths assert zero pending rows retain that hash (cross-table hole regression).
- T-NOTIF-1: notifier throws ⇒ postedAt stays null, attempts+1; next recovery retries.
- T-NOTIF-2: seed pending with attempts=MAX ⇒ recoverUnnotified() skips it entirely; row still returned by dashboard flow (INV-6 attempt cap).
- T-NOTIF-3: seed pending with timestamp now−49h, postedAt=null ⇒ recovery skips (age cap); manual approve path unaffected.
docs/HARDENING_CHECKLIST.md: emulator E2E (`adb emu sms send` numeric-sender caveat → handleSms seam), lifecycle matrix (screen-off, force-stop, reboot, deviceidle force-idle), kill-mid-flow crash repro + `dumpsys notification` assertions, notification denied/channel-off states, Play Protect procedure + INV-5 policy note verbatim.

## Verification
1. `.\gradlew.bat :app:testDebugUnitTest` all green (incl. legacy 11)
2. `.\gradlew.bat assembleDebug` (KSP validates v8 schema/index names)
3. Optional signed assembleRelease if keystore present
4. AGENTS.md: schema "currently 8"; rewrite guarantee line per INV-4; gotchas: cache-only rule, recovery sweep, INFERRED semantics. CODEBASE_INDEX §3/§4/§13 updated.

Execution order: enums → entities(+defaults annotations) → MIGRATION_7_8 → AutoBackupManager → DAOs → gateways → processor → adapters → INV-3 wrappers in action-receiver/dashboard → repo/UI tagging → hygiene → tests → builds → docs.
