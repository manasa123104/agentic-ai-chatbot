package com.agentic.chatbot.stage.template;

/**
 * Offline TypeScript (strict, CommonJS + ts-jest) code and tests: the banking fund transfer and the generic
 * submit / approve / reject workflow. Placeholders are filled by {@link PolyglotTemplates}.
 */
final class TsTemplates {

    private TsTemplates() {
    }

    static final String FUND = """
            /**
             * Fund transfer between bank accounts. Balances are integer cents, never floating point.
             * {{MARKER}}.
             */

            /** Business rule: each account may send at most this many cents per calendar day (10,000.00). */
            export const DAILY_LIMIT_CENTS = 1_000_000;

            export type TransferReason =
              | 'INVALID_AMOUNT'
              | 'SAME_ACCOUNT'
              | 'ACCOUNT_NOT_FOUND'
              | 'ACCOUNT_NOT_ACTIVE'
              | 'INSUFFICIENT_FUNDS'
              | 'DAILY_LIMIT_EXCEEDED'
              | 'DUPLICATE_ACCOUNT';

            /** Raised when a transfer breaks a business rule; the message is safe to show to the user. */
            export class TransferError extends Error {
              constructor(public readonly reason: TransferReason, message: string) {
                super(message);
                this.name = 'TransferError';
              }
            }

            export interface Account {
              readonly id: string;
              readonly owner: string;
              balanceCents: number;
              active: boolean;
            }

            /** Proof of a completed transfer. */
            export interface Receipt {
              readonly id: string;
              readonly fromId: string;
              readonly toId: string;
              readonly amountCents: number;
              readonly at: Date;
            }

            /** Holds accounts and moves money between them, keeping a ledger of every transfer. */
            export class FundTransferService {
              private readonly accounts = new Map<string, Account>();
              private readonly receipts: Receipt[] = [];
              private readonly sentByDay = new Map<string, number>();

              constructor(private readonly clock: () => Date = () => new Date()) {}

              /** Every completed transfer, oldest first. */
              get ledger(): readonly Receipt[] {
                return this.receipts;
              }

              /** Opens an account with an opening balance in cents and returns it. */
              openAccount(id: string, owner: string, balanceCents = 0): Account {
                // Business rule: an account can never be opened with a negative balance.
                if (!Number.isInteger(balanceCents) || balanceCents < 0) {
                  throw new TransferError('INVALID_AMOUNT', 'Opening balance must be zero or more cents');
                }
                if (this.accounts.has(id)) {
                  throw new TransferError('DUPLICATE_ACCOUNT', `Account ${id} already exists`);
                }
                const account: Account = { id, owner, balanceCents, active: true };
                this.accounts.set(id, account);
                return account;
              }

              /** Freezes an account so it can neither send nor receive money. */
              freeze(id: string): void {
                this.find(id).active = false;
              }

              /** Balance of an account in cents. */
              balance(id: string): number {
                return this.find(id).balanceCents;
              }

              /** Moves amountCents from one account to another and returns the receipt. */
              transfer(fromId: string, toId: string, amountCents: number): Receipt {
                // Business rule: the amount must be a positive whole number of cents.
                if (!Number.isInteger(amountCents) || amountCents <= 0) {
                  throw new TransferError('INVALID_AMOUNT', 'Amount must be a positive number of cents');
                }
                // Business rule: a transfer must go to a different account.
                if (fromId === toId) {
                  throw new TransferError('SAME_ACCOUNT', 'Cannot transfer to the same account');
                }
                const from = this.active(fromId);
                const to = this.active(toId);
                // Business rule: no overdraft; the balance must cover the full amount.
                if (from.balanceCents < amountCents) {
                  throw new TransferError('INSUFFICIENT_FUNDS', 'Insufficient funds');
                }
                // Business rule: total sent today plus this amount must stay within the daily limit.
                const dayKey = fromId + '|' + this.clock().toISOString().slice(0, 10);
                const sent = this.sentByDay.get(dayKey) ?? 0;
                if (sent + amountCents > DAILY_LIMIT_CENTS) {
                  throw new TransferError('DAILY_LIMIT_EXCEEDED', 'Daily transfer limit exceeded');
                }
                from.balanceCents -= amountCents;
                to.balanceCents += amountCents;
                this.sentByDay.set(dayKey, sent + amountCents);
                // Business rule: every successful transfer gets a unique id and is kept in the ledger for audit.
                const receipt: Receipt = {
                  id: 'TRF-' + String(this.receipts.length + 1).padStart(6, '0'),
                  fromId, toId, amountCents, at: this.clock()
                };
                this.receipts.push(receipt);
                return receipt;
              }

              private find(id: string): Account {
                const account = this.accounts.get(id);
                if (!account) {
                  throw new TransferError('ACCOUNT_NOT_FOUND', `Account ${id} not found`);
                }
                return account;
              }

              private active(id: string): Account {
                const account = this.find(id);
                // Business rule: frozen accounts can neither send nor receive money (AML / court orders).
                if (!account.active) {
                  throw new TransferError('ACCOUNT_NOT_ACTIVE', `Account ${id} is not active`);
                }
                return account;
              }
            }

            if (require.main === module) {
              const bank = new FundTransferService();
              bank.openAccount('ACC-1', 'Alice', 50000);
              bank.openAccount('ACC-2', 'Bob', 0);
              const receipt = bank.transfer('ACC-1', 'ACC-2', 12550);
              console.log(`${receipt.id}: moved ${receipt.amountCents} cents, ACC-1 now has ${bank.balance('ACC-1')}`);
            }
            """;

    static final String FUND_TEST = """
            // Jest (ts-jest) tests for the fund transfer rules.
            import { DAILY_LIMIT_CENTS, FundTransferService, TransferError, TransferReason } from '{{IMPORT}}';

            const FIXED = new Date('2026-01-01T09:00:00Z');
            let bank: FundTransferService;

            beforeEach(() => {
              bank = new FundTransferService(() => FIXED);
              bank.openAccount('ACC-1', 'Alice', 50000);
              bank.openAccount('ACC-2', 'Bob', 1000);
            });

            /** Reason code of the TransferError thrown by fn. */
            function reasonOf(fn: () => unknown): TransferReason {
              try {
                fn();
              } catch (e) {
                if (e instanceof TransferError) return e.reason;
                throw e;
              }
              throw new Error('expected a TransferError');
            }

            test('a valid transfer moves the money and records a receipt', () => {
              const receipt = bank.transfer('ACC-1', 'ACC-2', 2500);
              expect(receipt.id).toBe('TRF-000001');
              expect(bank.balance('ACC-1')).toBe(47500);
              expect(bank.balance('ACC-2')).toBe(3500);
              expect(bank.ledger).toHaveLength(1);
            });

            test('the amount must be a positive whole number of cents', () => {
              expect(reasonOf(() => bank.transfer('ACC-1', 'ACC-2', 0))).toBe('INVALID_AMOUNT');
              expect(reasonOf(() => bank.transfer('ACC-1', 'ACC-2', 10.5))).toBe('INVALID_AMOUNT');
            });

            test('money cannot be sent to the same account', () => {
              expect(reasonOf(() => bank.transfer('ACC-1', 'ACC-1', 100))).toBe('SAME_ACCOUNT');
            });

            test('there is no overdraft and a refused transfer changes nothing', () => {
              expect(reasonOf(() => bank.transfer('ACC-2', 'ACC-1', 1001))).toBe('INSUFFICIENT_FUNDS');
              expect(bank.balance('ACC-2')).toBe(1000);
              expect(bank.ledger).toHaveLength(0);
            });

            test('frozen and unknown accounts are refused', () => {
              bank.freeze('ACC-2');
              expect(reasonOf(() => bank.transfer('ACC-1', 'ACC-2', 100))).toBe('ACCOUNT_NOT_ACTIVE');
              expect(reasonOf(() => bank.transfer('ACC-1', 'NOPE', 100))).toBe('ACCOUNT_NOT_FOUND');
            });

            test('the daily limit applies per sending account', () => {
              bank.openAccount('ACC-3', 'Carol', DAILY_LIMIT_CENTS * 2);
              bank.transfer('ACC-3', 'ACC-1', DAILY_LIMIT_CENTS);
              expect(reasonOf(() => bank.transfer('ACC-3', 'ACC-1', 1))).toBe('DAILY_LIMIT_EXCEEDED');
            });
            """;

    static final String WORKFLOW = """
            /**
             * {{NAME}} requests for the {{DOMAIN}} domain: submit, approve, reject, with an audit trail.
             * {{MARKER}}.
             */

            /** Business rule: request details are limited to this many characters. */
            export const MAX_DETAILS = 500;

            /** States of a request. Only SUBMITTED requests can be decided; decisions are final. */
            export enum {{NAME}}Status {
              Submitted = 'SUBMITTED',
              Approved = 'APPROVED',
              Rejected = 'REJECTED',
            }

            /** Raised when an action breaks a business rule; the message is safe to show to the user. */
            export class {{NAME}}Error extends Error {
              constructor(message: string) {
                super(message);
                this.name = '{{NAME}}Error';
              }
            }

            /** A {{DOMAIN}} request raised by a user and decided by a reviewer. */
            export interface {{NAME}} {
              readonly reference: string;
              readonly requestedBy: string;
              readonly details: string;
              readonly createdAt: Date;
              status: {{NAME}}Status;
              decidedBy?: string;
              decisionNote?: string;
            }

            /** Submits, approves and rejects requests and keeps an audit trail. */
            export class {{NAME}}Service {
              private readonly requests = new Map<string, {{NAME}}>();
              private readonly audit: string[] = [];
              private sequence = 0;

              constructor(private readonly clock: () => Date = () => new Date()) {}

              /** Raises a new request and returns it in status SUBMITTED. */
              submit(requestedBy: string, details: string): {{NAME}} {
                // Business rule: every request must say who raised it and what is requested.
                if (!requestedBy?.trim()) throw new {{NAME}}Error('Requester is required');
                if (!details?.trim()) throw new {{NAME}}Error('Details are required');
                // Business rule: details are capped to keep requests reviewable.
                if (details.length > MAX_DETAILS) {
                  throw new {{NAME}}Error(`Details must be at most ${MAX_DETAILS} characters`);
                }
                this.sequence += 1;
                const reference = '{{REF_PREFIX}}-' + String(this.sequence).padStart(6, '0');
                const request: {{NAME}} = {
                  reference, requestedBy: requestedBy.trim(), details: details.trim(), createdAt: this.clock(),
                  status: {{NAME}}Status.Submitted
                };
                this.requests.set(reference, request);
                this.log(reference, 'SUBMITTED', requestedBy);
                return request;
              }

              /** Approves a pending request. */
              approve(reference: string, reviewer: string): {{NAME}} {
                const request = this.pending(reference);
                const who = requireReviewer(reviewer);
                // Business rule: {{CHECKER_RULE}}.
                if (request.requestedBy.toLowerCase() === who.toLowerCase()) {
                  throw new {{NAME}}Error('Requester cannot approve their own request');
                }
                request.status = {{NAME}}Status.Approved;
                request.decidedBy = who;
                this.log(reference, 'APPROVED', who);
                return request;
              }

              /** Rejects a pending request; a reason is mandatory. */
              reject(reference: string, reviewer: string, reason: string): {{NAME}} {
                const request = this.pending(reference);
                const who = requireReviewer(reviewer);
                // Business rule: a rejection must give the requester a reason.
                if (!reason?.trim()) throw new {{NAME}}Error('A reason is required to reject a request');
                request.status = {{NAME}}Status.Rejected;
                request.decidedBy = who;
                request.decisionNote = reason.trim();
                this.log(reference, 'REJECTED', who);
                return request;
              }

              /** Returns the request or throws if the reference is unknown. */
              find(reference: string): {{NAME}} {
                const request = this.requests.get(reference);
                if (!request) throw new {{NAME}}Error(`Request not found: ${reference}`);
                return request;
              }

              /** Every action taken, oldest first, as 'time reference ACTION by user'. */
              auditTrail(): string[] {
                return [...this.audit];
              }

              private pending(reference: string): {{NAME}} {
                const request = this.find(reference);
                // Business rule: decisions are final; only SUBMITTED requests can be decided.
                if (request.status !== {{NAME}}Status.Submitted) {
                  throw new {{NAME}}Error(`Request ${reference} is already ${request.status}`);
                }
                return request;
              }

              private log(reference: string, action: string, user: string): void {
                // Business rule: every action is recorded with who did it and when.
                this.audit.push(`${this.clock().toISOString()} ${reference} ${action} by ${user}`);
              }
            }

            function requireReviewer(reviewer: string): string {
              if (!reviewer?.trim()) throw new {{NAME}}Error('Reviewer is required');
              return reviewer.trim();
            }

            if (require.main === module) {
              const service = new {{NAME}}Service();
              const created = service.submit('alice', "Example {{DOMAIN}} request");
              console.log(`Created ${created.reference} with status ${created.status}`);
            }
            """;

    static final String WORKFLOW_TEST = """
            // Jest (ts-jest) tests for the {{NAME}} workflow ({{DOMAIN}}).
            import { MAX_DETAILS, {{NAME}}Error, {{NAME}}Service, {{NAME}}Status } from '{{IMPORT}}';

            const FIXED = new Date('2026-01-01T00:00:00Z');
            let service: {{NAME}}Service;

            beforeEach(() => {
              service = new {{NAME}}Service(() => FIXED);
            });

            test('submit creates a SUBMITTED request with a reference number', () => {
              const request = service.submit('alice', 'Need approval');
              expect(request.status).toBe({{NAME}}Status.Submitted);
              expect(request.reference).toBe('{{REF_PREFIX}}-000001');
              expect(service.find(request.reference)).toBe(request);
            });

            test.each([
              ['', 'x', 'Requester is required'],
              ['alice', '  ', 'Details are required'],
              ['alice', 'x'.repeat(MAX_DETAILS + 1), 'at most'],
            ])('submit(%p, ...) is rejected with a clear message', (who, details, message) => {
              expect(() => service.submit(who, details)).toThrow({{NAME}}Error);
              expect(() => service.submit(who, details)).toThrow(message);
            });

            test('a reviewer can approve', () => {
              const request = service.submit('alice', 'Need approval');
              const approved = service.approve(request.reference, 'bob');
              expect(approved.status).toBe({{NAME}}Status.Approved);
              expect(approved.decidedBy).toBe('bob');
            });

            test('the requester cannot approve their own request', () => {
              const request = service.submit('alice', 'Need approval');
              expect(() => service.approve(request.reference, 'ALICE')).toThrow('cannot approve their own request');
            });

            test('a rejection needs a reason', () => {
              const request = service.submit('alice', 'Need approval');
              expect(() => service.reject(request.reference, 'bob', ' ')).toThrow('reason is required');
              const rejected = service.reject(request.reference, 'bob', 'Missing documents');
              expect(rejected.status).toBe({{NAME}}Status.Rejected);
              expect(rejected.decisionNote).toBe('Missing documents');
            });

            test('decisions are final', () => {
              const request = service.submit('alice', 'Need approval');
              service.approve(request.reference, 'bob');
              expect(() => service.reject(request.reference, 'carol', 'Too late')).toThrow('already APPROVED');
            });

            test('an unknown reference is reported', () => {
              expect(() => service.find('NOPE')).toThrow('Request not found');
            });

            test('the audit trail records every action', () => {
              const request = service.submit('alice', 'Need approval');
              service.approve(request.reference, 'bob');
              const trail = service.auditTrail();
              expect(trail).toHaveLength(2);
              expect(trail[0]).toContain('SUBMITTED by alice');
              expect(trail[1]).toContain('APPROVED by bob');
            });
            """;
}
