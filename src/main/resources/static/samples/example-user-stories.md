# Feature: Fund transfer between accounts
Domain: Banking

Use this file as a sample upload on Stage bots → Business Analyst → “Already have user stories?”.
The input box accepts up to 55,000 characters of story text (typically dozens of stories). Each file may be up to 5 MB; a ZIP may contain up to 1,000 files.

## Story 1: Transfer money between accounts
As a retail banking customer, I want to transfer money from my account to another account, so that I can pay people and move savings without visiting a branch.

### Acceptance criteria
Scenario: Successful transfer
  Given account ACC-1001 is active with a balance of 1000.00
  And beneficiary account ACC-2002 is active
  When I transfer 250.00 from ACC-1001 to ACC-2002 with reference "Rent"
  Then 250.00 is debited from ACC-1001 and credited to ACC-2002
  And I see a confirmation with a transaction id starting with "TXN"

Scenario: Insufficient funds
  Given account ACC-1001 has a balance of 1000.00
  When I transfer 5000.00 to ACC-2002
  Then the transfer is rejected with "Insufficient funds"
  And neither balance changes

Scenario: Same source and destination
  When I transfer 10.00 from ACC-1001 to ACC-1001
  Then the transfer is rejected with "Cannot transfer to the same account"

## Story 2: Validate the amount
As a retail banking customer, I want invalid amounts to be rejected before anything is debited, so that I never send money by mistake.

### Acceptance criteria
Scenario: Zero or negative amount
  When I transfer 0.00 or -5.00
  Then the transfer is rejected with "Enter a valid amount greater than zero with at most 2 decimal places"

Scenario: More than two decimal places
  When I transfer 10.005
  Then the transfer is rejected with the same message

## Story 3: Daily transfer limit
As a bank risk officer, I want a daily outgoing limit of 10000.00 per account, so that losses from fraud are capped.

### Acceptance criteria
Scenario: Limit not exceeded
  Given ACC-1001 has already sent 9000.00 today
  When I transfer 1000.00
  Then the transfer succeeds and the remaining daily limit is 0.00

Scenario: Limit exceeded
  Given ACC-1001 has already sent 9000.00 today
  When I transfer 1000.01
  Then the transfer is rejected with "Daily transfer limit of 10000.00 exceeded"

## Story 4: Blocked accounts
As a compliance officer, I want frozen or closed accounts to be unable to send or receive transfers, so that we meet AML and court-order obligations.

### Acceptance criteria
Scenario: Frozen source account
  Given account ACC-3003 is frozen
  When its owner transfers 10.00 to ACC-2002
  Then the transfer is rejected with "Account is frozen"

Scenario: Closed destination account
  Given account ACC-2002 is closed
  When I transfer 10.00 from ACC-1001 to ACC-2002
  Then the transfer is rejected with "Beneficiary account is closed"

## Story 5: One-time password for large transfers
As a retail banking customer, I want to confirm transfers above 10000.00 with an OTP, so that a stolen session cannot empty my account.

### Acceptance criteria
Scenario: OTP required
  Given I am transferring 15000.00 from ACC-1001 to ACC-2002
  When I submit the transfer
  Then I am asked for a one-time password sent to my registered mobile
  And the money is not moved until the OTP is correct

Scenario: Wrong OTP
  When I enter an incorrect OTP three times
  Then the transfer is cancelled with "OTP failed"
  And neither balance changes

## Story 6: Transfer history
As a retail banking customer, I want to see my last transfers, so that I can check who I paid and when.

### Acceptance criteria
Scenario: List recent transfers
  Given I have made at least one transfer today
  When I open transfer history
  Then I see date, amount, beneficiary, reference and status for each transfer
  And they are newest first

Scenario: Empty history
  Given I have never transferred
  When I open transfer history
  Then I see "No transfers yet"
