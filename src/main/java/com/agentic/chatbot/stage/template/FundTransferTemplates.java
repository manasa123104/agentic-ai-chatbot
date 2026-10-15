package com.agentic.chatbot.stage.template;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline banking sample: fund transfer between accounts (plain Java 17) and its JUnit 5 tests.
 */
final class FundTransferTemplates {

    static final String PKG = "com.bank.transfer";
    private static final String MAIN = "src/main/java/com/bank/transfer/";
    private static final String TEST = "src/test/java/com/bank/transfer/";

    private FundTransferTemplates() {
    }

    static Map<String, String> code() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(MAIN + "Account.java", ACCOUNT);
        files.put(MAIN + "TransferRequest.java", TRANSFER_REQUEST);
        files.put(MAIN + "TransferReceipt.java", TRANSFER_RECEIPT);
        files.put(MAIN + "TransferException.java", TRANSFER_EXCEPTION);
        files.put(MAIN + "AccountRepository.java", ACCOUNT_REPOSITORY);
        files.put(MAIN + "FundTransferService.java", FUND_TRANSFER_SERVICE);
        return files;
    }

    static Map<String, String> tests() {
        return Map.of(TEST + "FundTransferServiceTest.java", FUND_TRANSFER_SERVICE_TEST);
    }

    private static final String ACCOUNT = """
            package com.bank.transfer;

            import java.math.BigDecimal;
            import java.util.Objects;

            /**
             * A customer's bank account that can send and receive fund transfers.
             * Balances are held as {@link BigDecimal} so money is never rounded by floating point.
             */
            public class Account {

                /** Lifecycle state of an account. */
                public enum Status {
                    /** Normal account; can send and receive money. */
                    ACTIVE,
                    /** Blocked by compliance (e.g. AML review or court order). */
                    FROZEN,
                    /** Closed by the customer or the bank. */
                    CLOSED
                }

                private final String accountNumber;
                private final String holderName;
                private BigDecimal balance;
                private Status status;

                /**
                 * Opens an active account.
                 *
                 * @param accountNumber  unique account number, e.g. ACC-1001
                 * @param holderName     name of the account holder
                 * @param openingBalance starting balance, must not be negative
                 */
                public Account(String accountNumber, String holderName, BigDecimal openingBalance) {
                    this(accountNumber, holderName, openingBalance, Status.ACTIVE);
                }

                /**
                 * Creates an account in the given state.
                 *
                 * @param accountNumber  unique account number
                 * @param holderName     name of the account holder
                 * @param openingBalance starting balance, must not be negative
                 * @param status         account state
                 */
                public Account(String accountNumber, String holderName, BigDecimal openingBalance, Status status) {
                    this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
                    this.holderName = Objects.requireNonNull(holderName, "holderName");
                    this.status = Objects.requireNonNull(status, "status");
                    // Business rule: an account can never be opened with a negative balance (no overdraft product here).
                    if (openingBalance == null || openingBalance.signum() < 0) {
                        throw new IllegalArgumentException("Opening balance must be zero or more");
                    }
                    this.balance = openingBalance;
                }

                /** @return the unique account number */
                public String getAccountNumber() {
                    return accountNumber;
                }

                /** @return the account holder's name */
                public String getHolderName() {
                    return holderName;
                }

                /** @return the current balance */
                public BigDecimal getBalance() {
                    return balance;
                }

                /** @return the current account state */
                public Status getStatus() {
                    return status;
                }

                /**
                 * Changes the account state, e.g. when compliance freezes it.
                 *
                 * @param status new state
                 */
                public void setStatus(Status status) {
                    this.status = Objects.requireNonNull(status, "status");
                }

                /**
                 * Business rule: only ACTIVE accounts may send or receive money.
                 *
                 * @return true when the account is active
                 */
                public boolean isActive() {
                    return status == Status.ACTIVE;
                }

                /** Takes money out; the service checks funds before calling this. */
                void debit(BigDecimal amount) {
                    balance = balance.subtract(amount);
                }

                /** Adds money in. */
                void credit(BigDecimal amount) {
                    balance = balance.add(amount);
                }
            }
            """;

    private static final String TRANSFER_REQUEST = """
            package com.bank.transfer;

            import java.math.BigDecimal;

            /**
             * A customer's instruction to move money between two accounts.
             *
             * @param fromAccount account to debit
             * @param toAccount   beneficiary account to credit
             * @param amount      amount in the account currency, at most 2 decimal places
             * @param reference   free-text reference shown on both statements (may be empty)
             */
            public record TransferRequest(String fromAccount, String toAccount, BigDecimal amount, String reference) {
            }
            """;

    private static final String TRANSFER_RECEIPT = """
            package com.bank.transfer;

            import java.math.BigDecimal;
            import java.time.Instant;

            /**
             * Confirmation of a completed transfer, kept in the ledger for audit.
             *
             * @param transactionId       unique id, e.g. TXN00000001
             * @param fromAccount         debited account
             * @param toAccount           credited account
             * @param amount              amount moved
             * @param reference           customer reference
             * @param sourceBalanceAfter  balance of the debited account after the transfer
             * @param executedAt          when the transfer was booked
             */
            public record TransferReceipt(String transactionId, String fromAccount, String toAccount, BigDecimal amount,
                                          String reference, BigDecimal sourceBalanceAfter, Instant executedAt) {
            }
            """;

    private static final String TRANSFER_EXCEPTION = """
            package com.bank.transfer;

            /**
             * Raised when a transfer breaks a business rule. Nothing is debited or credited when it is thrown.
             */
            public class TransferException extends RuntimeException {

                /** Why the transfer was refused. */
                public enum Reason {
                    INVALID_AMOUNT,
                    SAME_ACCOUNT,
                    ACCOUNT_NOT_FOUND,
                    ACCOUNT_NOT_ACTIVE,
                    INSUFFICIENT_FUNDS,
                    DAILY_LIMIT_EXCEEDED
                }

                private final Reason reason;

                /**
                 * @param reason  machine-readable reason
                 * @param message message safe to show to the customer
                 */
                public TransferException(Reason reason, String message) {
                    super(message);
                    this.reason = reason;
                }

                /** @return why the transfer was refused */
                public Reason getReason() {
                    return reason;
                }
            }
            """;

    private static final String ACCOUNT_REPOSITORY = """
            package com.bank.transfer;

            import java.util.Map;
            import java.util.Optional;
            import java.util.concurrent.ConcurrentHashMap;

            /**
             * In-memory account store. Replace with a JPA repository when wiring this into Spring Boot.
             */
            public class AccountRepository {

                private final Map<String, Account> accounts = new ConcurrentHashMap<>();

                /**
                 * Adds or replaces an account.
                 *
                 * @param account account to store
                 * @return the stored account
                 */
                public Account save(Account account) {
                    accounts.put(account.getAccountNumber(), account);
                    return account;
                }

                /**
                 * @param accountNumber account number to look up
                 * @return the account, or empty when it does not exist
                 */
                public Optional<Account> findByNumber(String accountNumber) {
                    return accountNumber == null ? Optional.empty() : Optional.ofNullable(accounts.get(accountNumber.trim()));
                }
            }
            """;

    private static final String FUND_TRANSFER_SERVICE = """
            package com.bank.transfer;

            import java.math.BigDecimal;
            import java.time.Clock;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.util.ArrayList;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            /**
             * Moves money between accounts and enforces the bank's transfer rules:
             * valid amount, different accounts, both accounts active, sufficient funds and a daily outgoing limit.
             * Either the whole transfer happens (debit, credit, ledger entry) or nothing changes.
             */
            public class FundTransferService {

                /** Business rule: each account may send at most this much per calendar day. */
                public static final BigDecimal DAILY_LIMIT = new BigDecimal("10000.00");

                private final AccountRepository accounts;
                private final Clock clock;
                private final Map<String, BigDecimal> sentToday = new HashMap<>();
                private final List<TransferReceipt> ledger = new ArrayList<>();
                private long sequence;

                /**
                 * @param accounts where accounts are looked up
                 * @param clock    time source; inject a fixed clock in tests
                 */
                public FundTransferService(AccountRepository accounts, Clock clock) {
                    this.accounts = accounts;
                    this.clock = clock;
                }

                /** @param accounts where accounts are looked up; uses the UTC system clock */
                public FundTransferService(AccountRepository accounts) {
                    this(accounts, Clock.systemUTC());
                }

                /**
                 * Transfers money from one account to another.
                 *
                 * @param request what to transfer
                 * @return receipt with the new transaction id
                 * @throws TransferException when any business rule is broken; balances are then unchanged
                 */
                public synchronized TransferReceipt transfer(TransferRequest request) {
                    BigDecimal amount = request.amount();
                    // Business rule: amount must be positive with at most 2 decimal places (no fractions of a cent).
                    if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                        throw new TransferException(TransferException.Reason.INVALID_AMOUNT,
                                "Enter a valid amount greater than zero with at most 2 decimal places");
                    }
                    // Business rule: a transfer must go to a different account.
                    if (request.fromAccount() != null && request.fromAccount().equals(request.toAccount())) {
                        throw new TransferException(TransferException.Reason.SAME_ACCOUNT, "Cannot transfer to the same account");
                    }
                    Account from = find(request.fromAccount());
                    Account to = find(request.toAccount());
                    // Business rule: frozen or closed accounts can neither send nor receive (AML / court orders).
                    requireActive(from);
                    requireActive(to);
                    // Business rule: no overdraft; the balance must cover the full amount.
                    if (from.getBalance().compareTo(amount) < 0) {
                        throw new TransferException(TransferException.Reason.INSUFFICIENT_FUNDS, "Insufficient funds");
                    }
                    // Business rule: total sent today plus this amount must stay within the daily limit.
                    String limitKey = from.getAccountNumber() + "|" + LocalDate.now(clock);
                    BigDecimal alreadySent = sentToday.getOrDefault(limitKey, BigDecimal.ZERO);
                    if (alreadySent.add(amount).compareTo(DAILY_LIMIT) > 0) {
                        throw new TransferException(TransferException.Reason.DAILY_LIMIT_EXCEEDED,
                                "Daily transfer limit of " + DAILY_LIMIT + " exceeded");
                    }

                    from.debit(amount);
                    to.credit(amount);
                    sentToday.put(limitKey, alreadySent.add(amount));
                    // Business rule: every successful transfer gets a unique id and is kept in the ledger for audit.
                    TransferReceipt receipt = new TransferReceipt(
                            "TXN" + String.format("%08d", ++sequence),
                            from.getAccountNumber(), to.getAccountNumber(), amount,
                            request.reference() == null ? "" : request.reference().trim(),
                            from.getBalance(), Instant.now(clock));
                    ledger.add(receipt);
                    return receipt;
                }

                /**
                 * Transfers into or out of an account, oldest first.
                 *
                 * @param accountNumber account to report on
                 * @return matching ledger entries
                 */
                public synchronized List<TransferReceipt> history(String accountNumber) {
                    return ledger.stream()
                            .filter(r -> r.fromAccount().equals(accountNumber) || r.toAccount().equals(accountNumber))
                            .toList();
                }

                /**
                 * How much more the account may send today.
                 *
                 * @param accountNumber source account
                 * @return remaining daily limit, never negative
                 */
                public synchronized BigDecimal remainingDailyLimit(String accountNumber) {
                    BigDecimal sent = sentToday.getOrDefault(accountNumber + "|" + LocalDate.now(clock), BigDecimal.ZERO);
                    return DAILY_LIMIT.subtract(sent).max(BigDecimal.ZERO);
                }

                private Account find(String accountNumber) {
                    return accounts.findByNumber(accountNumber).orElseThrow(() -> new TransferException(
                            TransferException.Reason.ACCOUNT_NOT_FOUND, "Account not found: " + accountNumber));
                }

                private static void requireActive(Account account) {
                    if (!account.isActive()) {
                        throw new TransferException(TransferException.Reason.ACCOUNT_NOT_ACTIVE,
                                "Account " + account.getAccountNumber() + " is not active");
                    }
                }
            }
            """;

    private static final String FUND_TRANSFER_SERVICE_TEST = """
            package com.bank.transfer;

            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Test;

            import java.math.BigDecimal;
            import java.time.Clock;
            import java.time.Instant;
            import java.time.ZoneOffset;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertThrows;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            /**
             * Unit tests for {@link FundTransferService}, one per acceptance criterion of the fund transfer stories.
             */
            class FundTransferServiceTest {

                private AccountRepository repository;
                private FundTransferService service;

                @BeforeEach
                void setUp() {
                    repository = new AccountRepository();
                    repository.save(new Account("ACC-1001", "Asha Rao", new BigDecimal("1000.00")));
                    repository.save(new Account("ACC-2002", "Ben Lee", new BigDecimal("50.00")));
                    repository.save(new Account("ACC-3003", "Frozen Co", new BigDecimal("500.00"), Account.Status.FROZEN));
                    repository.save(new Account("ACC-4004", "Rich Corp", new BigDecimal("50000.00")));
                    Clock fixed = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
                    service = new FundTransferService(repository, fixed);
                }

                private BigDecimal balance(String account) {
                    return repository.findByNumber(account).orElseThrow().getBalance();
                }

                private TransferException.Reason refused(String from, String to, String amount) {
                    return assertThrows(TransferException.class,
                            () -> service.transfer(new TransferRequest(from, to, new BigDecimal(amount), "test"))).getReason();
                }

                @Test
                @DisplayName("Successful transfer debits the source, credits the beneficiary and returns a receipt")
                void transfersMoneyBetweenActiveAccounts() {
                    TransferReceipt receipt = service.transfer(
                            new TransferRequest("ACC-1001", "ACC-2002", new BigDecimal("250.00"), "Rent"));

                    assertTrue(receipt.transactionId().startsWith("TXN"));
                    assertEquals(new BigDecimal("750.00"), balance("ACC-1001"));
                    assertEquals(new BigDecimal("300.00"), balance("ACC-2002"));
                    assertEquals(new BigDecimal("750.00"), receipt.sourceBalanceAfter());
                    assertEquals("Rent", receipt.reference());
                }

                @Test
                @DisplayName("Insufficient funds: rejected and no balance changes")
                void rejectsTransferWhenFundsAreInsufficient() {
                    assertEquals(TransferException.Reason.INSUFFICIENT_FUNDS, refused("ACC-1001", "ACC-2002", "5000.00"));
                    assertEquals(new BigDecimal("1000.00"), balance("ACC-1001"));
                    assertEquals(new BigDecimal("50.00"), balance("ACC-2002"));
                }

                @Test
                @DisplayName("Same source and destination is rejected")
                void rejectsTransferToSameAccount() {
                    assertEquals(TransferException.Reason.SAME_ACCOUNT, refused("ACC-1001", "ACC-1001", "10.00"));
                }

                @Test
                @DisplayName("Zero, negative and over-precise amounts are rejected")
                void rejectsInvalidAmounts() {
                    assertEquals(TransferException.Reason.INVALID_AMOUNT, refused("ACC-1001", "ACC-2002", "0.00"));
                    assertEquals(TransferException.Reason.INVALID_AMOUNT, refused("ACC-1001", "ACC-2002", "-5.00"));
                    assertEquals(TransferException.Reason.INVALID_AMOUNT, refused("ACC-1001", "ACC-2002", "10.005"));
                }

                @Test
                @DisplayName("Frozen accounts can neither send nor receive")
                void rejectsFrozenAccounts() {
                    assertEquals(TransferException.Reason.ACCOUNT_NOT_ACTIVE, refused("ACC-3003", "ACC-2002", "10.00"));
                    assertEquals(TransferException.Reason.ACCOUNT_NOT_ACTIVE, refused("ACC-1001", "ACC-3003", "10.00"));
                }

                @Test
                @DisplayName("Unknown beneficiary is rejected")
                void rejectsUnknownAccount() {
                    TransferException e = assertThrows(TransferException.class, () -> service.transfer(
                            new TransferRequest("ACC-1001", "ACC-9999", new BigDecimal("10.00"), "")));
                    assertEquals(TransferException.Reason.ACCOUNT_NOT_FOUND, e.getReason());
                    assertEquals("Account not found: ACC-9999", e.getMessage());
                }

                @Test
                @DisplayName("Daily limit: up to 10000.00 allowed, one cent more rejected")
                void enforcesDailyLimit() {
                    service.transfer(new TransferRequest("ACC-4004", "ACC-2002", new BigDecimal("9000.00"), "Invoice 1"));
                    assertEquals(TransferException.Reason.DAILY_LIMIT_EXCEEDED, refused("ACC-4004", "ACC-2002", "1000.01"));

                    service.transfer(new TransferRequest("ACC-4004", "ACC-2002", new BigDecimal("1000.00"), "Invoice 2"));
                    assertEquals(0, service.remainingDailyLimit("ACC-4004").signum());
                }

                @Test
                @DisplayName("History lists the transfer for both accounts")
                void recordsHistoryForBothAccounts() {
                    TransferReceipt receipt = service.transfer(
                            new TransferRequest("ACC-1001", "ACC-2002", new BigDecimal("25.00"), "Lunch"));

                    assertEquals(1, service.history("ACC-1001").size());
                    assertEquals(receipt, service.history("ACC-2002").get(0));
                    assertTrue(service.history("ACC-4004").isEmpty());
                }
            }
            """;
}
