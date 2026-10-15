package com.agentic.chatbot.stage.template;

/**
 * Offline Go code and tests (package main under app/, standard library only): the banking fund transfer and the
 * generic submit / approve / reject workflow. Placeholders are filled by {@link PolyglotTemplates}.
 */
final class GoTemplates {

    private GoTemplates() {
    }

    static final String FUND = """
            // Fund transfer between bank accounts. Balances are integer cents, never floating point.
            // {{MARKER}}.
            package main

            import (
                "errors"
                "fmt"
                "time"
            )

            // DailyLimitCents is a business rule: each account may send at most this many cents per calendar day.
            const DailyLimitCents int64 = 1_000_000

            // Reasons a transfer is refused; compare with errors.Is.
            var (
                ErrInvalidAmount     = errors.New("amount must be a positive number of cents")
                ErrSameAccount       = errors.New("cannot transfer to the same account")
                ErrAccountNotFound   = errors.New("account not found")
                ErrAccountNotActive  = errors.New("account is not active")
                ErrInsufficientFunds = errors.New("insufficient funds")
                ErrDailyLimit        = errors.New("daily transfer limit exceeded")
                ErrDuplicateAccount  = errors.New("account already exists")
            )

            // Account is a bank account.
            type Account struct {
                ID           string
                Owner        string
                BalanceCents int64
                Active       bool
            }

            // Receipt is proof of a completed transfer.
            type Receipt struct {
                ID          string
                FromID      string
                ToID        string
                AmountCents int64
                At          time.Time
            }

            // FundTransferService holds accounts and moves money between them, keeping a ledger of every transfer.
            type FundTransferService struct {
                clock     func() time.Time
                accounts  map[string]*Account
                ledger    []Receipt
                sentByDay map[string]int64
            }

            // NewFundTransferService creates a service; clock may be nil to use the current time.
            func NewFundTransferService(clock func() time.Time) *FundTransferService {
                if clock == nil {
                    clock = time.Now
                }
                return &FundTransferService{clock: clock, accounts: map[string]*Account{}, sentByDay: map[string]int64{}}
            }

            // OpenAccount opens an account with an opening balance in cents.
            func (s *FundTransferService) OpenAccount(id, owner string, balanceCents int64) (*Account, error) {
                // Business rule: an account can never be opened with a negative balance.
                if balanceCents < 0 {
                    return nil, ErrInvalidAmount
                }
                if _, exists := s.accounts[id]; exists {
                    return nil, fmt.Errorf("%w: %s", ErrDuplicateAccount, id)
                }
                account := &Account{ID: id, Owner: owner, BalanceCents: balanceCents, Active: true}
                s.accounts[id] = account
                return account, nil
            }

            // Freeze stops an account from sending or receiving money.
            func (s *FundTransferService) Freeze(id string) error {
                account, err := s.find(id)
                if err != nil {
                    return err
                }
                account.Active = false
                return nil
            }

            // Balance returns the balance of an account in cents.
            func (s *FundTransferService) Balance(id string) (int64, error) {
                account, err := s.find(id)
                if err != nil {
                    return 0, err
                }
                return account.BalanceCents, nil
            }

            // Ledger returns every completed transfer, oldest first.
            func (s *FundTransferService) Ledger() []Receipt {
                return append([]Receipt(nil), s.ledger...)
            }

            // Transfer moves amountCents from one account to another and returns the receipt.
            func (s *FundTransferService) Transfer(fromID, toID string, amountCents int64) (Receipt, error) {
                // Business rule: the amount must be a positive whole number of cents.
                if amountCents <= 0 {
                    return Receipt{}, ErrInvalidAmount
                }
                // Business rule: a transfer must go to a different account.
                if fromID == toID {
                    return Receipt{}, ErrSameAccount
                }
                from, err := s.active(fromID)
                if err != nil {
                    return Receipt{}, err
                }
                to, err := s.active(toID)
                if err != nil {
                    return Receipt{}, err
                }
                // Business rule: no overdraft; the balance must cover the full amount.
                if from.BalanceCents < amountCents {
                    return Receipt{}, ErrInsufficientFunds
                }
                // Business rule: total sent today plus this amount must stay within the daily limit.
                dayKey := fromID + "|" + s.clock().Format("2006-01-02")
                if s.sentByDay[dayKey]+amountCents > DailyLimitCents {
                    return Receipt{}, ErrDailyLimit
                }
                from.BalanceCents -= amountCents
                to.BalanceCents += amountCents
                s.sentByDay[dayKey] += amountCents
                // Business rule: every successful transfer gets a unique id and is kept in the ledger for audit.
                receipt := Receipt{ID: fmt.Sprintf("TRF-%06d", len(s.ledger)+1), FromID: fromID, ToID: toID,
                    AmountCents: amountCents, At: s.clock()}
                s.ledger = append(s.ledger, receipt)
                return receipt, nil
            }

            func (s *FundTransferService) find(id string) (*Account, error) {
                account, ok := s.accounts[id]
                if !ok {
                    return nil, fmt.Errorf("%w: %s", ErrAccountNotFound, id)
                }
                return account, nil
            }

            func (s *FundTransferService) active(id string) (*Account, error) {
                account, err := s.find(id)
                if err != nil {
                    return nil, err
                }
                // Business rule: frozen accounts can neither send nor receive money (AML / court orders).
                if !account.Active {
                    return nil, fmt.Errorf("%w: %s", ErrAccountNotActive, id)
                }
                return account, nil
            }
            """;

    static final String FUND_MAIN = """
            package main

            import "fmt"

            // main demonstrates the fund transfer service.
            func main() {
                bank := NewFundTransferService(nil)
                _, _ = bank.OpenAccount("ACC-1", "Alice", 50000)
                _, _ = bank.OpenAccount("ACC-2", "Bob", 0)
                receipt, err := bank.Transfer("ACC-1", "ACC-2", 12550)
                if err != nil {
                    fmt.Println("Transfer refused:", err)
                    return
                }
                fmt.Println(receipt.ID, "moved", receipt.AmountCents, "cents")
            }
            """;

    static final String FUND_TEST = """
            package main

            import (
                "errors"
                "testing"
                "time"
            )

            var fixed = time.Date(2026, 1, 1, 9, 0, 0, 0, time.UTC)

            func newBank(t *testing.T) *FundTransferService {
                t.Helper()
                bank := NewFundTransferService(func() time.Time { return fixed })
                if _, err := bank.OpenAccount("ACC-1", "Alice", 50000); err != nil {
                    t.Fatal(err)
                }
                if _, err := bank.OpenAccount("ACC-2", "Bob", 1000); err != nil {
                    t.Fatal(err)
                }
                return bank
            }

            func balance(t *testing.T, bank *FundTransferService, id string) int64 {
                t.Helper()
                b, err := bank.Balance(id)
                if err != nil {
                    t.Fatal(err)
                }
                return b
            }

            // A valid transfer moves the money and records a receipt.
            func TestValidTransfer(t *testing.T) {
                bank := newBank(t)
                receipt, err := bank.Transfer("ACC-1", "ACC-2", 2500)
                if err != nil {
                    t.Fatal(err)
                }
                if receipt.ID != "TRF-000001" {
                    t.Errorf("receipt id = %s", receipt.ID)
                }
                if got := balance(t, bank, "ACC-1"); got != 47500 {
                    t.Errorf("ACC-1 balance = %d, want 47500", got)
                }
                if got := balance(t, bank, "ACC-2"); got != 3500 {
                    t.Errorf("ACC-2 balance = %d, want 3500", got)
                }
                if len(bank.Ledger()) != 1 {
                    t.Errorf("ledger has %d entries, want 1", len(bank.Ledger()))
                }
            }

            // Each business rule refuses the transfer with its own error and changes nothing.
            func TestTransferRules(t *testing.T) {
                cases := []struct {
                    name   string
                    from   string
                    to     string
                    amount int64
                    want   error
                }{
                    {"zero amount", "ACC-1", "ACC-2", 0, ErrInvalidAmount},
                    {"negative amount", "ACC-1", "ACC-2", -5, ErrInvalidAmount},
                    {"same account", "ACC-1", "ACC-1", 100, ErrSameAccount},
                    {"unknown account", "ACC-1", "NOPE", 100, ErrAccountNotFound},
                    {"overdraft", "ACC-2", "ACC-1", 1001, ErrInsufficientFunds},
                }
                for _, c := range cases {
                    t.Run(c.name, func(t *testing.T) {
                        bank := newBank(t)
                        if _, err := bank.Transfer(c.from, c.to, c.amount); !errors.Is(err, c.want) {
                            t.Errorf("got %v, want %v", err, c.want)
                        }
                        if got := balance(t, bank, "ACC-2"); got != 1000 {
                            t.Errorf("ACC-2 balance changed to %d", got)
                        }
                    })
                }
            }

            // Frozen accounts can neither send nor receive.
            func TestFrozenAccountIsRefused(t *testing.T) {
                bank := newBank(t)
                if err := bank.Freeze("ACC-2"); err != nil {
                    t.Fatal(err)
                }
                if _, err := bank.Transfer("ACC-1", "ACC-2", 100); !errors.Is(err, ErrAccountNotActive) {
                    t.Errorf("got %v, want %v", err, ErrAccountNotActive)
                }
            }

            // The daily limit applies per sending account.
            func TestDailyLimit(t *testing.T) {
                bank := newBank(t)
                if _, err := bank.OpenAccount("ACC-3", "Carol", DailyLimitCents*2); err != nil {
                    t.Fatal(err)
                }
                if _, err := bank.Transfer("ACC-3", "ACC-1", DailyLimitCents); err != nil {
                    t.Fatal(err)
                }
                if _, err := bank.Transfer("ACC-3", "ACC-1", 1); !errors.Is(err, ErrDailyLimit) {
                    t.Errorf("got %v, want %v", err, ErrDailyLimit)
                }
            }
            """;

    static final String WORKFLOW = """
            // {{NAME}} requests for the {{DOMAIN}} domain: submit, approve, reject, with an audit trail.
            // {{MARKER}}.
            package main

            import (
                "errors"
                "fmt"
                "strings"
                "time"
            )

            // MaxDetails is a business rule: request details are limited to this many characters.
            const MaxDetails = 500

            // {{NAME}}Status is the state of a request. Only Submitted requests can be decided; decisions are final.
            type {{NAME}}Status string

            // Request states.
            const (
                Submitted {{NAME}}Status = "SUBMITTED"
                Approved  {{NAME}}Status = "APPROVED"
                Rejected  {{NAME}}Status = "REJECTED"
            )

            // Err{{NAME}} wraps every business-rule violation; the message is safe to show to the user.
            var Err{{NAME}} = errors.New("{{NAME}} rule violated")

            func ruleError(format string, args ...any) error {
                return fmt.Errorf("%w: %s", Err{{NAME}}, fmt.Sprintf(format, args...))
            }

            // {{NAME}} is a {{DOMAIN}} request raised by a user and decided by a reviewer.
            type {{NAME}} struct {
                Reference    string
                RequestedBy  string
                Details      string
                CreatedAt    time.Time
                Status       {{NAME}}Status
                DecidedBy    string
                DecisionNote string
            }

            // {{NAME}}Service submits, approves and rejects requests and keeps an audit trail.
            type {{NAME}}Service struct {
                clock    func() time.Time
                requests map[string]*{{NAME}}
                audit    []string
                sequence int
            }

            // New{{NAME}}Service creates a service; clock may be nil to use the current time.
            func New{{NAME}}Service(clock func() time.Time) *{{NAME}}Service {
                if clock == nil {
                    clock = time.Now
                }
                return &{{NAME}}Service{clock: clock, requests: map[string]*{{NAME}}{}}
            }

            // Submit raises a new request and returns it in status Submitted.
            func (s *{{NAME}}Service) Submit(requestedBy, details string) (*{{NAME}}, error) {
                // Business rule: every request must say who raised it and what is requested.
                if strings.TrimSpace(requestedBy) == "" {
                    return nil, ruleError("Requester is required")
                }
                if strings.TrimSpace(details) == "" {
                    return nil, ruleError("Details are required")
                }
                // Business rule: details are capped to keep requests reviewable.
                if len(details) > MaxDetails {
                    return nil, ruleError("Details must be at most %d characters", MaxDetails)
                }
                s.sequence++
                reference := fmt.Sprintf("{{REF_PREFIX}}-%06d", s.sequence)
                request := &{{NAME}}{Reference: reference, RequestedBy: strings.TrimSpace(requestedBy),
                    Details: strings.TrimSpace(details), CreatedAt: s.clock(), Status: Submitted}
                s.requests[reference] = request
                s.log(reference, "SUBMITTED", requestedBy)
                return request, nil
            }

            // Approve approves a pending request.
            func (s *{{NAME}}Service) Approve(reference, reviewer string) (*{{NAME}}, error) {
                request, err := s.pending(reference)
                if err != nil {
                    return nil, err
                }
                who := strings.TrimSpace(reviewer)
                if who == "" {
                    return nil, ruleError("Reviewer is required")
                }
                // Business rule: {{CHECKER_RULE}}.
                if strings.EqualFold(request.RequestedBy, who) {
                    return nil, ruleError("Requester cannot approve their own request")
                }
                request.Status = Approved
                request.DecidedBy = who
                s.log(reference, "APPROVED", who)
                return request, nil
            }

            // Reject rejects a pending request; a reason is mandatory.
            func (s *{{NAME}}Service) Reject(reference, reviewer, reason string) (*{{NAME}}, error) {
                request, err := s.pending(reference)
                if err != nil {
                    return nil, err
                }
                who := strings.TrimSpace(reviewer)
                if who == "" {
                    return nil, ruleError("Reviewer is required")
                }
                // Business rule: a rejection must give the requester a reason.
                if strings.TrimSpace(reason) == "" {
                    return nil, ruleError("A reason is required to reject a request")
                }
                request.Status = Rejected
                request.DecidedBy = who
                request.DecisionNote = strings.TrimSpace(reason)
                s.log(reference, "REJECTED", who)
                return request, nil
            }

            // Find returns the request or an error if the reference is unknown.
            func (s *{{NAME}}Service) Find(reference string) (*{{NAME}}, error) {
                request, ok := s.requests[reference]
                if !ok {
                    return nil, ruleError("Request not found: %s", reference)
                }
                return request, nil
            }

            // AuditTrail returns every action taken, oldest first, as "time reference ACTION by user".
            func (s *{{NAME}}Service) AuditTrail() []string {
                return append([]string(nil), s.audit...)
            }

            func (s *{{NAME}}Service) pending(reference string) (*{{NAME}}, error) {
                request, err := s.Find(reference)
                if err != nil {
                    return nil, err
                }
                // Business rule: decisions are final; only Submitted requests can be decided.
                if request.Status != Submitted {
                    return nil, ruleError("Request %s is already %s", reference, request.Status)
                }
                return request, nil
            }

            func (s *{{NAME}}Service) log(reference, action, user string) {
                // Business rule: every action is recorded with who did it and when.
                s.audit = append(s.audit, fmt.Sprintf("%s %s %s by %s", s.clock().Format(time.RFC3339), reference, action, user))
            }
            """;

    static final String WORKFLOW_MAIN = """
            package main

            import "fmt"

            // main demonstrates the {{NAME}} workflow.
            func main() {
                service := New{{NAME}}Service(nil)
                created, err := service.Submit("alice", "Example {{DOMAIN}} request")
                if err != nil {
                    fmt.Println(err)
                    return
                }
                fmt.Println("Created", created.Reference, "with status", created.Status)
            }
            """;

    static final String WORKFLOW_TEST = """
            package main

            import (
                "errors"
                "strings"
                "testing"
                "time"
            )

            func newService() *{{NAME}}Service {
                return New{{NAME}}Service(func() time.Time { return time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC) })
            }

            func mustSubmit(t *testing.T, s *{{NAME}}Service) *{{NAME}} {
                t.Helper()
                request, err := s.Submit("alice", "Need approval")
                if err != nil {
                    t.Fatal(err)
                }
                return request
            }

            func wantRuleError(t *testing.T, err error, text string) {
                t.Helper()
                if !errors.Is(err, Err{{NAME}}) || !strings.Contains(err.Error(), text) {
                    t.Errorf("got %v, want a rule error containing %q", err, text)
                }
            }

            // Submit creates a Submitted request with a reference number.
            func TestSubmit(t *testing.T) {
                s := newService()
                request := mustSubmit(t, s)
                if request.Status != Submitted || request.Reference != "{{REF_PREFIX}}-000001" {
                    t.Errorf("got %+v", request)
                }
                if found, err := s.Find(request.Reference); err != nil || found != request {
                    t.Errorf("Find returned %v, %v", found, err)
                }
            }

            // Invalid submissions are rejected with a clear message.
            func TestSubmitValidatesInput(t *testing.T) {
                s := newService()
                _, err := s.Submit("", "x")
                wantRuleError(t, err, "Requester is required")
                _, err = s.Submit("alice", "  ")
                wantRuleError(t, err, "Details are required")
                _, err = s.Submit("alice", strings.Repeat("x", MaxDetails+1))
                wantRuleError(t, err, "at most")
            }

            // A reviewer can approve, but never their own request.
            func TestApprove(t *testing.T) {
                s := newService()
                request := mustSubmit(t, s)
                _, err := s.Approve(request.Reference, "ALICE")
                wantRuleError(t, err, "cannot approve their own request")
                approved, err := s.Approve(request.Reference, "bob")
                if err != nil || approved.Status != Approved || approved.DecidedBy != "bob" {
                    t.Errorf("got %+v, %v", approved, err)
                }
            }

            // A rejection needs a reason, and decisions are final.
            func TestRejectAndFinalDecisions(t *testing.T) {
                s := newService()
                request := mustSubmit(t, s)
                _, err := s.Reject(request.Reference, "bob", " ")
                wantRuleError(t, err, "reason is required")
                rejected, err := s.Reject(request.Reference, "bob", "Missing documents")
                if err != nil || rejected.Status != Rejected || rejected.DecisionNote != "Missing documents" {
                    t.Errorf("got %+v, %v", rejected, err)
                }
                _, err = s.Approve(request.Reference, "carol")
                wantRuleError(t, err, "already REJECTED")
            }

            // Unknown references are reported and every action is audited.
            func TestFindAndAuditTrail(t *testing.T) {
                s := newService()
                _, err := s.Find("NOPE")
                wantRuleError(t, err, "Request not found")
                request := mustSubmit(t, s)
                if _, err := s.Approve(request.Reference, "bob"); err != nil {
                    t.Fatal(err)
                }
                trail := s.AuditTrail()
                if len(trail) != 2 || !strings.Contains(trail[0], "SUBMITTED by alice") || !strings.Contains(trail[1], "APPROVED by bob") {
                    t.Errorf("audit trail = %v", trail)
                }
            }
            """;
}
