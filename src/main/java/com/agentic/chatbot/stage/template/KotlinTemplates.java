package com.agentic.chatbot.stage.template;

/**
 * Offline Kotlin (JVM, Gradle, kotlin.test on JUnit 5) code and tests: the banking fund transfer and the generic
 * submit / approve / reject workflow. Placeholders are filled by {@link PolyglotTemplates}.
 */
final class KotlinTemplates {

    private KotlinTemplates() {
    }

    static final String FUND = """
            // Fund transfer between bank accounts. Money is BigDecimal, never binary floating point.
            // {{MARKER}}.
            package {{PKG}}

            import java.math.BigDecimal
            import java.time.Clock
            import java.time.Instant
            import java.time.LocalDate

            /** Why a transfer was refused. */
            enum class TransferReason {
                INVALID_AMOUNT, SAME_ACCOUNT, ACCOUNT_NOT_FOUND, ACCOUNT_NOT_ACTIVE, INSUFFICIENT_FUNDS,
                DAILY_LIMIT_EXCEEDED, DUPLICATE_ACCOUNT
            }

            /** Raised when a transfer breaks a business rule; the message is safe to show to the user. */
            class TransferException(val reason: TransferReason, message: String) : RuntimeException(message)

            /** A bank account. */
            class Account(val id: String, val owner: String, openingBalance: BigDecimal) {
                var balance: BigDecimal = openingBalance
                    internal set
                var active: Boolean = true
                    internal set
            }

            /** Proof of a completed transfer. */
            data class TransferReceipt(val id: String, val fromId: String, val toId: String, val amount: BigDecimal, val at: Instant)

            /** Holds accounts and moves money between them, keeping a ledger of every transfer. */
            class FundTransferService(private val clock: Clock = Clock.systemUTC()) {
                private val accounts = mutableMapOf<String, Account>()
                private val receipts = mutableListOf<TransferReceipt>()
                private val sentByDay = mutableMapOf<String, BigDecimal>()

                /** Every completed transfer, oldest first. */
                val ledger: List<TransferReceipt>
                    get() = receipts.toList()

                /** Opens an account with an opening balance and returns it. */
                fun openAccount(id: String, owner: String, openingBalance: BigDecimal): Account {
                    // Business rule: an account can never be opened with a negative balance.
                    if (openingBalance.signum() < 0) {
                        throw TransferException(TransferReason.INVALID_AMOUNT, "Opening balance cannot be negative")
                    }
                    if (id in accounts) {
                        throw TransferException(TransferReason.DUPLICATE_ACCOUNT, "Account $id already exists")
                    }
                    return Account(id, owner, openingBalance).also { accounts[id] = it }
                }

                /** Freezes an account so it can neither send nor receive money. */
                fun freeze(id: String) {
                    find(id).active = false
                }

                /** Current balance of an account. */
                fun balance(id: String): BigDecimal = find(id).balance

                /** Moves money from one account to another and returns the receipt. */
                fun transfer(fromId: String, toId: String, amount: BigDecimal): TransferReceipt {
                    // Business rule: amount must be positive with at most 2 decimal places (no fractions of a cent).
                    if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                        throw TransferException(TransferReason.INVALID_AMOUNT, "Amount must be positive with at most 2 decimals")
                    }
                    // Business rule: a transfer must go to a different account.
                    if (fromId == toId) {
                        throw TransferException(TransferReason.SAME_ACCOUNT, "Cannot transfer to the same account")
                    }
                    val from = activeAccount(fromId)
                    val to = activeAccount(toId)
                    // Business rule: no overdraft; the balance must cover the full amount.
                    if (from.balance < amount) {
                        throw TransferException(TransferReason.INSUFFICIENT_FUNDS, "Insufficient funds")
                    }
                    // Business rule: total sent today plus this amount must stay within the daily limit.
                    val dayKey = fromId + "|" + LocalDate.now(clock)
                    val sent = sentByDay[dayKey] ?: BigDecimal.ZERO
                    if (sent + amount > DAILY_LIMIT) {
                        throw TransferException(TransferReason.DAILY_LIMIT_EXCEEDED, "Daily transfer limit of $DAILY_LIMIT exceeded")
                    }
                    from.balance = from.balance - amount
                    to.balance = to.balance + amount
                    sentByDay[dayKey] = sent + amount
                    // Business rule: every successful transfer gets a unique id and is kept in the ledger for audit.
                    val receipt = TransferReceipt("TRF-%06d".format(receipts.size + 1), fromId, toId, amount, clock.instant())
                    receipts += receipt
                    return receipt
                }

                private fun find(id: String): Account =
                    accounts[id] ?: throw TransferException(TransferReason.ACCOUNT_NOT_FOUND, "Account $id not found")

                private fun activeAccount(id: String): Account {
                    val account = find(id)
                    // Business rule: frozen accounts can neither send nor receive money (AML / court orders).
                    if (!account.active) {
                        throw TransferException(TransferReason.ACCOUNT_NOT_ACTIVE, "Account $id is not active")
                    }
                    return account
                }

                companion object {
                    /** Business rule: each account may send at most this much per calendar day. */
                    val DAILY_LIMIT = BigDecimal("10000.00")
                }
            }
            """;

    static final String FUND_MAIN = """
            package {{PKG}}

            import java.math.BigDecimal

            /** Demonstrates the fund transfer service from the command line. */
            fun main() {
                val bank = FundTransferService()
                bank.openAccount("ACC-1", "Alice", BigDecimal("500.00"))
                bank.openAccount("ACC-2", "Bob", BigDecimal.ZERO)
                val receipt = bank.transfer("ACC-1", "ACC-2", BigDecimal("125.50"))
                println("${receipt.id}: moved ${receipt.amount}, ACC-1 now has ${bank.balance("ACC-1")}")
            }
            """;

    static final String FUND_TEST = """
            package {{PKG}}

            import java.math.BigDecimal
            import java.time.Clock
            import java.time.Instant
            import java.time.ZoneOffset
            import kotlin.test.BeforeTest
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertFailsWith
            import kotlin.test.assertTrue

            class FundTransferServiceTest {
                private val clock = Clock.fixed(Instant.parse("2026-01-01T09:00:00Z"), ZoneOffset.UTC)
                private lateinit var bank: FundTransferService

                @BeforeTest
                fun setUp() {
                    bank = FundTransferService(clock)
                    bank.openAccount("ACC-1", "Alice", BigDecimal("500.00"))
                    bank.openAccount("ACC-2", "Bob", BigDecimal("10.00"))
                }

                private fun refused(from: String, to: String, amount: String): TransferReason =
                    assertFailsWith<TransferException> { bank.transfer(from, to, BigDecimal(amount)) }.reason

                private fun assertMoney(expected: String, actual: BigDecimal) =
                    assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")

                @Test
                fun validTransferMovesTheMoneyAndRecordsAReceipt() {
                    val receipt = bank.transfer("ACC-1", "ACC-2", BigDecimal("25.00"))
                    assertEquals("TRF-000001", receipt.id)
                    assertMoney("475.00", bank.balance("ACC-1"))
                    assertMoney("35.00", bank.balance("ACC-2"))
                    assertEquals(1, bank.ledger.size)
                }

                @Test
                fun amountMustBePositiveWholeCents() {
                    assertEquals(TransferReason.INVALID_AMOUNT, refused("ACC-1", "ACC-2", "0"))
                    assertEquals(TransferReason.INVALID_AMOUNT, refused("ACC-1", "ACC-2", "-5"))
                    assertEquals(TransferReason.INVALID_AMOUNT, refused("ACC-1", "ACC-2", "0.001"))
                }

                @Test
                fun moneyCannotBeSentToTheSameAccount() {
                    assertEquals(TransferReason.SAME_ACCOUNT, refused("ACC-1", "ACC-1", "1"))
                }

                @Test
                fun thereIsNoOverdraftAndARefusedTransferChangesNothing() {
                    assertEquals(TransferReason.INSUFFICIENT_FUNDS, refused("ACC-2", "ACC-1", "10.01"))
                    assertMoney("10.00", bank.balance("ACC-2"))
                    assertTrue(bank.ledger.isEmpty())
                }

                @Test
                fun frozenAndUnknownAccountsAreRefused() {
                    bank.freeze("ACC-2")
                    assertEquals(TransferReason.ACCOUNT_NOT_ACTIVE, refused("ACC-1", "ACC-2", "1"))
                    assertEquals(TransferReason.ACCOUNT_NOT_FOUND, refused("ACC-1", "NOPE", "1"))
                }

                @Test
                fun theDailyLimitAppliesPerSendingAccount() {
                    bank.openAccount("ACC-3", "Carol", FundTransferService.DAILY_LIMIT * BigDecimal(2))
                    bank.transfer("ACC-3", "ACC-1", FundTransferService.DAILY_LIMIT)
                    assertEquals(TransferReason.DAILY_LIMIT_EXCEEDED, refused("ACC-3", "ACC-1", "0.01"))
                }
            }
            """;

    static final String WORKFLOW = """
            // {{NAME}} requests for the {{DOMAIN}} domain: submit, approve, reject, with an audit trail.
            // {{MARKER}}.
            package {{PKG}}

            import java.time.Clock
            import java.time.Instant

            /** States of a request. Only SUBMITTED requests can be decided; decisions are final. */
            enum class {{NAME}}Status { SUBMITTED, APPROVED, REJECTED }

            /** Raised when an action breaks a business rule; the message is safe to show to the user. */
            class {{NAME}}Exception(message: String) : RuntimeException(message)

            /** A {{DOMAIN}} request raised by a user and decided by a reviewer. */
            class {{NAME}}(val reference: String, val requestedBy: String, val details: String, val createdAt: Instant) {
                var status: {{NAME}}Status = {{NAME}}Status.SUBMITTED
                    internal set
                var decidedBy: String? = null
                    internal set
                var decisionNote: String? = null
                    internal set
            }

            /** Submits, approves and rejects requests and keeps an audit trail. */
            class {{NAME}}Service(private val clock: Clock = Clock.systemUTC()) {
                private val requests = mutableMapOf<String, {{NAME}}>()
                private val audit = mutableListOf<String>()
                private var sequence = 0

                /** Raises a new request and returns it in status SUBMITTED. */
                fun submit(requestedBy: String, details: String): {{NAME}} {
                    // Business rule: every request must say who raised it and what is requested.
                    if (requestedBy.isBlank()) throw {{NAME}}Exception("Requester is required")
                    if (details.isBlank()) throw {{NAME}}Exception("Details are required")
                    // Business rule: details are capped to keep requests reviewable.
                    if (details.length > MAX_DETAILS) throw {{NAME}}Exception("Details must be at most $MAX_DETAILS characters")
                    sequence++
                    val reference = "{{REF_PREFIX}}-%06d".format(sequence)
                    val request = {{NAME}}(reference, requestedBy.trim(), details.trim(), clock.instant())
                    requests[reference] = request
                    log(reference, "SUBMITTED", requestedBy)
                    return request
                }

                /** Approves a pending request. */
                fun approve(reference: String, reviewer: String): {{NAME}} {
                    val request = pending(reference)
                    val who = requireReviewer(reviewer)
                    // Business rule: {{CHECKER_RULE}}.
                    if (request.requestedBy.equals(who, ignoreCase = true)) {
                        throw {{NAME}}Exception("Requester cannot approve their own request")
                    }
                    request.status = {{NAME}}Status.APPROVED
                    request.decidedBy = who
                    log(reference, "APPROVED", who)
                    return request
                }

                /** Rejects a pending request; a reason is mandatory. */
                fun reject(reference: String, reviewer: String, reason: String): {{NAME}} {
                    val request = pending(reference)
                    val who = requireReviewer(reviewer)
                    // Business rule: a rejection must give the requester a reason.
                    if (reason.isBlank()) throw {{NAME}}Exception("A reason is required to reject a request")
                    request.status = {{NAME}}Status.REJECTED
                    request.decidedBy = who
                    request.decisionNote = reason.trim()
                    log(reference, "REJECTED", who)
                    return request
                }

                /** Returns the request or throws if the reference is unknown. */
                fun find(reference: String): {{NAME}} =
                    requests[reference] ?: throw {{NAME}}Exception("Request not found: $reference")

                /** Every action taken, oldest first, as "time reference ACTION by user". */
                fun auditTrail(): List<String> = audit.toList()

                private fun pending(reference: String): {{NAME}} {
                    val request = find(reference)
                    // Business rule: decisions are final; only SUBMITTED requests can be decided.
                    if (request.status != {{NAME}}Status.SUBMITTED) {
                        throw {{NAME}}Exception("Request $reference is already ${request.status}")
                    }
                    return request
                }

                private fun requireReviewer(reviewer: String): String {
                    if (reviewer.isBlank()) throw {{NAME}}Exception("Reviewer is required")
                    return reviewer.trim()
                }

                private fun log(reference: String, action: String, user: String) {
                    // Business rule: every action is recorded with who did it and when.
                    audit += "${clock.instant()} $reference $action by $user"
                }

                companion object {
                    /** Business rule: request details are limited to this many characters. */
                    const val MAX_DETAILS = 500
                }
            }
            """;

    static final String WORKFLOW_MAIN = """
            package {{PKG}}

            /** Demonstrates the {{NAME}} workflow from the command line. */
            fun main() {
                val service = {{NAME}}Service()
                val created = service.submit("alice", "Example {{DOMAIN}} request")
                println("Created ${created.reference} with status ${created.status}")
            }
            """;

    static final String WORKFLOW_TEST = """
            package {{PKG}}

            import java.time.Clock
            import java.time.Instant
            import java.time.ZoneOffset
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertFailsWith
            import kotlin.test.assertSame
            import kotlin.test.assertTrue

            class {{NAME}}ServiceTest {
                private val service = {{NAME}}Service(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))

                private fun failure(block: () -> Unit): String =
                    assertFailsWith<{{NAME}}Exception> { block() }.message ?: ""

                @Test
                fun submitCreatesASubmittedRequestWithAReference() {
                    val request = service.submit("alice", "Need approval")
                    assertEquals({{NAME}}Status.SUBMITTED, request.status)
                    assertEquals("{{REF_PREFIX}}-000001", request.reference)
                    assertSame(request, service.find(request.reference))
                }

                @Test
                fun submitValidatesInput() {
                    assertTrue(failure { service.submit("", "x") }.contains("Requester is required"))
                    assertTrue(failure { service.submit("alice", "  ") }.contains("Details are required"))
                    val tooLong = "x".repeat({{NAME}}Service.MAX_DETAILS + 1)
                    assertTrue(failure { service.submit("alice", tooLong) }.contains("at most"))
                }

                @Test
                fun aReviewerCanApproveButNotTheirOwnRequest() {
                    val request = service.submit("alice", "Need approval")
                    assertTrue(failure { service.approve(request.reference, "ALICE") }.contains("cannot approve their own request"))
                    val approved = service.approve(request.reference, "bob")
                    assertEquals({{NAME}}Status.APPROVED, approved.status)
                    assertEquals("bob", approved.decidedBy)
                }

                @Test
                fun aRejectionNeedsAReasonAndDecisionsAreFinal() {
                    val request = service.submit("alice", "Need approval")
                    assertTrue(failure { service.reject(request.reference, "bob", " ") }.contains("reason is required"))
                    val rejected = service.reject(request.reference, "bob", "Missing documents")
                    assertEquals({{NAME}}Status.REJECTED, rejected.status)
                    assertEquals("Missing documents", rejected.decisionNote)
                    assertTrue(failure { service.approve(request.reference, "carol") }.contains("already REJECTED"))
                }

                @Test
                fun unknownReferencesAreReportedAndEveryActionIsAudited() {
                    assertTrue(failure { service.find("NOPE") }.contains("Request not found"))
                    val request = service.submit("alice", "Need approval")
                    service.approve(request.reference, "bob")
                    val trail = service.auditTrail()
                    assertEquals(2, trail.size)
                    assertTrue(trail[0].contains("SUBMITTED by alice"))
                    assertTrue(trail[1].contains("APPROVED by bob"))
                }
            }
            """;

    static final String BUILD_GRADLE = """
            plugins {
                kotlin("jvm") version "2.0.21"
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation(kotlin("test"))
            }

            kotlin {
                jvmToolchain(17)
            }

            tasks.test {
                useJUnitPlatform()
            }
            """;
}
