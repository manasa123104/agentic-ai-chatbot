package com.agentic.chatbot.stage.template;

/**
 * Offline C# (.NET 8, xUnit) code and tests: the banking fund transfer and the generic submit / approve / reject
 * workflow. Code goes to src/App (console project App), tests to tests/App.Tests. Placeholders are filled by
 * {@link PolyglotTemplates}.
 */
final class CSharpTemplates {

    private CSharpTemplates() {
    }

    static final String FUND = """
            // Fund transfer between bank accounts. Money is decimal, never binary floating point.
            // {{MARKER}}.
            using System;
            using System.Collections.Generic;
            using System.Globalization;

            namespace App;

            /// <summary>Why a transfer was refused.</summary>
            public enum TransferReason
            {
                InvalidAmount,
                SameAccount,
                AccountNotFound,
                AccountNotActive,
                InsufficientFunds,
                DailyLimitExceeded,
                DuplicateAccount
            }

            /// <summary>Raised when a transfer breaks a business rule; the message is safe to show to the user.</summary>
            public sealed class TransferException : Exception
            {
                public TransferException(TransferReason reason, string message) : base(message) => Reason = reason;

                /// <summary>Stable reason code.</summary>
                public TransferReason Reason { get; }
            }

            /// <summary>A bank account.</summary>
            public sealed class Account
            {
                public Account(string id, string owner, decimal balance)
                {
                    Id = id;
                    Owner = owner;
                    Balance = balance;
                }

                public string Id { get; }
                public string Owner { get; }
                public decimal Balance { get; internal set; }
                public bool Active { get; internal set; } = true;
            }

            /// <summary>Proof of a completed transfer.</summary>
            public sealed record TransferReceipt(string Id, string FromId, string ToId, decimal Amount, DateTime At);

            /// <summary>Holds accounts and moves money between them, keeping a ledger of every transfer.</summary>
            public sealed class FundTransferService
            {
                /// <summary>Business rule: each account may send at most this much per calendar day.</summary>
                public const decimal DailyLimit = 10_000.00m;

                private readonly Dictionary<string, Account> _accounts = new();
                private readonly List<TransferReceipt> _ledger = new();
                private readonly Dictionary<string, decimal> _sentByDay = new();
                private readonly Func<DateTime> _clock;

                /// <param name="clock">Time source, replaceable in tests; defaults to UTC now.</param>
                public FundTransferService(Func<DateTime>? clock = null) => _clock = clock ?? (() => DateTime.UtcNow);

                /// <summary>Every completed transfer, oldest first.</summary>
                public IReadOnlyList<TransferReceipt> Ledger => _ledger;

                /// <summary>Opens an account with an opening balance and returns it.</summary>
                public Account OpenAccount(string id, string owner, decimal openingBalance)
                {
                    // Business rule: an account can never be opened with a negative balance.
                    if (openingBalance < 0)
                        throw new TransferException(TransferReason.InvalidAmount, "Opening balance cannot be negative");
                    if (_accounts.ContainsKey(id))
                        throw new TransferException(TransferReason.DuplicateAccount, $"Account {id} already exists");
                    var account = new Account(id, owner, openingBalance);
                    _accounts[id] = account;
                    return account;
                }

                /// <summary>Freezes an account so it can neither send nor receive money.</summary>
                public void Freeze(string id) => Find(id).Active = false;

                /// <summary>Current balance of an account.</summary>
                public decimal Balance(string id) => Find(id).Balance;

                /// <summary>Moves money from one account to another and returns the receipt.</summary>
                public TransferReceipt Transfer(string fromId, string toId, decimal amount)
                {
                    // Business rule: amount must be positive with at most 2 decimal places (no fractions of a cent).
                    if (amount <= 0 || decimal.Round(amount, 2) != amount)
                        throw new TransferException(TransferReason.InvalidAmount, "Amount must be positive with at most 2 decimals");
                    // Business rule: a transfer must go to a different account.
                    if (fromId == toId)
                        throw new TransferException(TransferReason.SameAccount, "Cannot transfer to the same account");
                    var from = ActiveAccount(fromId);
                    var to = ActiveAccount(toId);
                    // Business rule: no overdraft; the balance must cover the full amount.
                    if (from.Balance < amount)
                        throw new TransferException(TransferReason.InsufficientFunds, "Insufficient funds");
                    // Business rule: total sent today plus this amount must stay within the daily limit.
                    var dayKey = fromId + "|" + _clock().ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
                    _sentByDay.TryGetValue(dayKey, out var sent);
                    if (sent + amount > DailyLimit)
                        throw new TransferException(TransferReason.DailyLimitExceeded, $"Daily transfer limit of {DailyLimit} exceeded");
                    from.Balance -= amount;
                    to.Balance += amount;
                    _sentByDay[dayKey] = sent + amount;
                    // Business rule: every successful transfer gets a unique id and is kept in the ledger for audit.
                    var receipt = new TransferReceipt($"TRF-{_ledger.Count + 1:D6}", fromId, toId, amount, _clock());
                    _ledger.Add(receipt);
                    return receipt;
                }

                private Account Find(string id) =>
                    _accounts.TryGetValue(id, out var account)
                        ? account
                        : throw new TransferException(TransferReason.AccountNotFound, $"Account {id} not found");

                private Account ActiveAccount(string id)
                {
                    var account = Find(id);
                    // Business rule: frozen accounts can neither send nor receive money (AML / court orders).
                    if (!account.Active)
                        throw new TransferException(TransferReason.AccountNotActive, $"Account {id} is not active");
                    return account;
                }
            }
            """;

    static final String FUND_PROGRAM = """
            using System;

            namespace App;

            /// <summary>Console entry point that demonstrates the fund transfer service.</summary>
            public static class Program
            {
                public static void Main()
                {
                    var bank = new FundTransferService();
                    bank.OpenAccount("ACC-1", "Alice", 500.00m);
                    bank.OpenAccount("ACC-2", "Bob", 0m);
                    var receipt = bank.Transfer("ACC-1", "ACC-2", 125.50m);
                    Console.WriteLine($"{receipt.Id}: moved {receipt.Amount}, ACC-1 now has {bank.Balance("ACC-1")}");
                }
            }
            """;

    static final String FUND_TEST = """
            // xUnit tests for the fund transfer rules.
            using System;
            using App;
            using Xunit;

            namespace App.Tests;

            public class FundTransferServiceTests
            {
                private static readonly DateTime Fixed = new(2026, 1, 1, 9, 0, 0, DateTimeKind.Utc);
                private readonly FundTransferService _bank = new(() => Fixed);

                public FundTransferServiceTests()
                {
                    _bank.OpenAccount("ACC-1", "Alice", 500.00m);
                    _bank.OpenAccount("ACC-2", "Bob", 10.00m);
                }

                private TransferReason Refused(string from, string to, decimal amount) =>
                    Assert.Throws<TransferException>(() => _bank.Transfer(from, to, amount)).Reason;

                [Fact]
                public void ValidTransferMovesTheMoneyAndRecordsAReceipt()
                {
                    var receipt = _bank.Transfer("ACC-1", "ACC-2", 25.00m);
                    Assert.Equal("TRF-000001", receipt.Id);
                    Assert.Equal(475.00m, _bank.Balance("ACC-1"));
                    Assert.Equal(35.00m, _bank.Balance("ACC-2"));
                    Assert.Single(_bank.Ledger);
                }

                [Theory]
                [InlineData(0.0)]
                [InlineData(-5.0)]
                [InlineData(0.001)]
                public void AmountMustBePositiveWholeCents(double amount)
                {
                    Assert.Equal(TransferReason.InvalidAmount, Refused("ACC-1", "ACC-2", (decimal)amount));
                }

                [Fact]
                public void MoneyCannotBeSentToTheSameAccount()
                {
                    Assert.Equal(TransferReason.SameAccount, Refused("ACC-1", "ACC-1", 1m));
                }

                [Fact]
                public void ThereIsNoOverdraftAndARefusedTransferChangesNothing()
                {
                    Assert.Equal(TransferReason.InsufficientFunds, Refused("ACC-2", "ACC-1", 10.01m));
                    Assert.Equal(10.00m, _bank.Balance("ACC-2"));
                    Assert.Empty(_bank.Ledger);
                }

                [Fact]
                public void FrozenAndUnknownAccountsAreRefused()
                {
                    _bank.Freeze("ACC-2");
                    Assert.Equal(TransferReason.AccountNotActive, Refused("ACC-1", "ACC-2", 1m));
                    Assert.Equal(TransferReason.AccountNotFound, Refused("ACC-1", "NOPE", 1m));
                }

                [Fact]
                public void TheDailyLimitAppliesPerSendingAccount()
                {
                    _bank.OpenAccount("ACC-3", "Carol", FundTransferService.DailyLimit * 2);
                    _bank.Transfer("ACC-3", "ACC-1", FundTransferService.DailyLimit);
                    Assert.Equal(TransferReason.DailyLimitExceeded, Refused("ACC-3", "ACC-1", 0.01m));
                }
            }
            """;

    static final String WORKFLOW = """
            // {{NAME}} requests for the {{DOMAIN}} domain: submit, approve, reject, with an audit trail.
            // {{MARKER}}.
            using System;
            using System.Collections.Generic;
            using System.Globalization;

            namespace App;

            /// <summary>States of a request. Only Submitted requests can be decided; decisions are final.</summary>
            public enum {{NAME}}Status
            {
                Submitted,
                Approved,
                Rejected
            }

            /// <summary>Raised when an action breaks a business rule; the message is safe to show to the user.</summary>
            public sealed class {{NAME}}Exception : Exception
            {
                public {{NAME}}Exception(string message) : base(message)
                {
                }
            }

            /// <summary>A {{DOMAIN}} request raised by a user and decided by a reviewer.</summary>
            public sealed class {{NAME}}Request
            {
                public {{NAME}}Request(string reference, string requestedBy, string details, DateTime createdAt)
                {
                    Reference = reference;
                    RequestedBy = requestedBy;
                    Details = details;
                    CreatedAt = createdAt;
                }

                public string Reference { get; }
                public string RequestedBy { get; }
                public string Details { get; }
                public DateTime CreatedAt { get; }
                public {{NAME}}Status Status { get; internal set; } = {{NAME}}Status.Submitted;
                public string? DecidedBy { get; internal set; }
                public string? DecisionNote { get; internal set; }
            }

            /// <summary>Submits, approves and rejects requests and keeps an audit trail.</summary>
            public sealed class {{NAME}}Service
            {
                /// <summary>Business rule: request details are limited to this many characters.</summary>
                public const int MaxDetails = 500;

                private readonly Dictionary<string, {{NAME}}Request> _requests = new();
                private readonly List<string> _audit = new();
                private readonly Func<DateTime> _clock;
                private int _sequence;

                /// <param name="clock">Time source, replaceable in tests; defaults to UTC now.</param>
                public {{NAME}}Service(Func<DateTime>? clock = null) => _clock = clock ?? (() => DateTime.UtcNow);

                /// <summary>Raises a new request and returns it in status Submitted.</summary>
                public {{NAME}}Request Submit(string requestedBy, string details)
                {
                    // Business rule: every request must say who raised it and what is requested.
                    if (string.IsNullOrWhiteSpace(requestedBy)) throw new {{NAME}}Exception("Requester is required");
                    if (string.IsNullOrWhiteSpace(details)) throw new {{NAME}}Exception("Details are required");
                    // Business rule: details are capped to keep requests reviewable.
                    if (details.Length > MaxDetails)
                        throw new {{NAME}}Exception($"Details must be at most {MaxDetails} characters");
                    _sequence++;
                    var reference = $"{{REF_PREFIX}}-{_sequence:D6}";
                    var request = new {{NAME}}Request(reference, requestedBy.Trim(), details.Trim(), _clock());
                    _requests[reference] = request;
                    Log(reference, "SUBMITTED", requestedBy);
                    return request;
                }

                /// <summary>Approves a pending request.</summary>
                public {{NAME}}Request Approve(string reference, string reviewer)
                {
                    var request = Pending(reference);
                    var who = RequireReviewer(reviewer);
                    // Business rule: {{CHECKER_RULE}}.
                    if (string.Equals(request.RequestedBy, who, StringComparison.OrdinalIgnoreCase))
                        throw new {{NAME}}Exception("Requester cannot approve their own request");
                    request.Status = {{NAME}}Status.Approved;
                    request.DecidedBy = who;
                    Log(reference, "APPROVED", who);
                    return request;
                }

                /// <summary>Rejects a pending request; a reason is mandatory.</summary>
                public {{NAME}}Request Reject(string reference, string reviewer, string reason)
                {
                    var request = Pending(reference);
                    var who = RequireReviewer(reviewer);
                    // Business rule: a rejection must give the requester a reason.
                    if (string.IsNullOrWhiteSpace(reason))
                        throw new {{NAME}}Exception("A reason is required to reject a request");
                    request.Status = {{NAME}}Status.Rejected;
                    request.DecidedBy = who;
                    request.DecisionNote = reason.Trim();
                    Log(reference, "REJECTED", who);
                    return request;
                }

                /// <summary>Returns the request or throws if the reference is unknown.</summary>
                public {{NAME}}Request Find(string reference) =>
                    _requests.TryGetValue(reference, out var request)
                        ? request
                        : throw new {{NAME}}Exception($"Request not found: {reference}");

                /// <summary>Every action taken, oldest first, as "time reference ACTION by user".</summary>
                public IReadOnlyList<string> AuditTrail() => _audit.AsReadOnly();

                private {{NAME}}Request Pending(string reference)
                {
                    var request = Find(reference);
                    // Business rule: decisions are final; only Submitted requests can be decided.
                    if (request.Status != {{NAME}}Status.Submitted)
                        throw new {{NAME}}Exception($"Request {reference} is already {request.Status.ToString().ToUpperInvariant()}");
                    return request;
                }

                private static string RequireReviewer(string reviewer) =>
                    string.IsNullOrWhiteSpace(reviewer) ? throw new {{NAME}}Exception("Reviewer is required") : reviewer.Trim();

                private void Log(string reference, string action, string user)
                {
                    // Business rule: every action is recorded with who did it and when.
                    _audit.Add($"{_clock().ToString("o", CultureInfo.InvariantCulture)} {reference} {action} by {user}");
                }
            }
            """;

    static final String WORKFLOW_PROGRAM = """
            using System;

            namespace App;

            /// <summary>Console entry point that demonstrates the {{NAME}} workflow.</summary>
            public static class Program
            {
                public static void Main()
                {
                    var service = new {{NAME}}Service();
                    var created = service.Submit("alice", "Example {{DOMAIN}} request");
                    Console.WriteLine($"Created {created.Reference} with status {created.Status}");
                }
            }
            """;

    static final String WORKFLOW_TEST = """
            // xUnit tests for the {{NAME}} workflow ({{DOMAIN}}).
            using System;
            using App;
            using Xunit;

            namespace App.Tests;

            public class {{NAME}}ServiceTests
            {
                private static readonly DateTime Fixed = new(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc);
                private readonly {{NAME}}Service _service = new(() => Fixed);

                [Fact]
                public void SubmitCreatesASubmittedRequestWithAReference()
                {
                    var request = _service.Submit("alice", "Need approval");
                    Assert.Equal({{NAME}}Status.Submitted, request.Status);
                    Assert.Equal("{{REF_PREFIX}}-000001", request.Reference);
                    Assert.Same(request, _service.Find(request.Reference));
                }

                [Theory]
                [InlineData("", "x", "Requester is required")]
                [InlineData("alice", "  ", "Details are required")]
                public void SubmitValidatesInput(string who, string details, string message)
                {
                    var e = Assert.Throws<{{NAME}}Exception>(() => _service.Submit(who, details));
                    Assert.Contains(message, e.Message);
                }

                [Fact]
                public void DetailsAreCapped()
                {
                    var e = Assert.Throws<{{NAME}}Exception>(() => _service.Submit("alice", new string('x', {{NAME}}Service.MaxDetails + 1)));
                    Assert.Contains("at most", e.Message);
                }

                [Fact]
                public void AReviewerCanApprove()
                {
                    var request = _service.Submit("alice", "Need approval");
                    var approved = _service.Approve(request.Reference, "bob");
                    Assert.Equal({{NAME}}Status.Approved, approved.Status);
                    Assert.Equal("bob", approved.DecidedBy);
                }

                [Fact]
                public void TheRequesterCannotApproveTheirOwnRequest()
                {
                    var request = _service.Submit("alice", "Need approval");
                    var e = Assert.Throws<{{NAME}}Exception>(() => _service.Approve(request.Reference, "ALICE"));
                    Assert.Contains("cannot approve their own request", e.Message);
                }

                [Fact]
                public void ARejectionNeedsAReason()
                {
                    var request = _service.Submit("alice", "Need approval");
                    Assert.Throws<{{NAME}}Exception>(() => _service.Reject(request.Reference, "bob", " "));
                    var rejected = _service.Reject(request.Reference, "bob", "Missing documents");
                    Assert.Equal({{NAME}}Status.Rejected, rejected.Status);
                    Assert.Equal("Missing documents", rejected.DecisionNote);
                }

                [Fact]
                public void DecisionsAreFinal()
                {
                    var request = _service.Submit("alice", "Need approval");
                    _service.Approve(request.Reference, "bob");
                    var e = Assert.Throws<{{NAME}}Exception>(() => _service.Reject(request.Reference, "carol", "Too late"));
                    Assert.Contains("already APPROVED", e.Message);
                }

                [Fact]
                public void TheAuditTrailRecordsEveryAction()
                {
                    var request = _service.Submit("alice", "Need approval");
                    _service.Approve(request.Reference, "bob");
                    var trail = _service.AuditTrail();
                    Assert.Equal(2, trail.Count);
                    Assert.Contains("SUBMITTED by alice", trail[0]);
                    Assert.Contains("APPROVED by bob", trail[1]);
                }
            }
            """;

    static final String APP_CSPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <OutputType>Exe</OutputType>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <AssemblyName>App</AssemblyName>
                <RootNamespace>App</RootNamespace>
              </PropertyGroup>
            </Project>
            """;

    static final String TEST_CSPROJ = """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <Nullable>enable</Nullable>
                <IsPackable>false</IsPackable>
              </PropertyGroup>
              <ItemGroup>
                <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.11.1" />
                <PackageReference Include="xunit" Version="2.9.2" />
                <PackageReference Include="xunit.runner.visualstudio" Version="2.8.2" />
              </ItemGroup>
              <ItemGroup>
                <ProjectReference Include="../../src/App/App.csproj" />
              </ItemGroup>
            </Project>
            """;
}
