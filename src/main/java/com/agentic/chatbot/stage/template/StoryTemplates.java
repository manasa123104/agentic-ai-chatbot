package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;

import java.util.Locale;

/**
 * Offline Business Analyst output: user stories with Given/When/Then acceptance criteria.
 */
public final class StoryTemplates {

    private StoryTemplates() {
    }

    public static String stories(StageDomain domain, String requirement) {
        return switch (StageSample.detect(requirement, domain)) {
            case FUND_TRANSFER -> FUND_TRANSFER;
            case SHIPMENT_TRACKING -> SHIPMENT_TRACKING;
            case GENERIC -> generic(domain, requirement);
        };
    }

    private static final String FUND_TRANSFER = """
            # Feature: Fund transfer between accounts
            Domain: Banking

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
              Then the transfer is rejected with "Account ACC-3003 is not active"

            Scenario: Unknown beneficiary
              When I transfer 10.00 to ACC-9999 which does not exist
              Then the transfer is rejected with "Account not found: ACC-9999"

            ## Story 5: Transfer history
            As a retail banking customer, I want to see the transfers into and out of my account, so that I can reconcile my statement.

            ### Acceptance criteria
            Scenario: History shows both sides
              Given I transferred 250.00 from ACC-1001 to ACC-2002
              When either account owner opens the transfer history
              Then the transfer appears with its transaction id, amount and reference

            ## Assumptions
            - All amounts are in the account currency with two decimal places; currency conversion is out of scope.
            - The daily limit is per source account per calendar day (UTC).
            - Every successful transfer gets a unique transaction id and is kept for audit.
            """;

    private static final String SHIPMENT_TRACKING = """
            # Feature: Shipment tracking
            Domain: Logistics

            ## Story 1: Book a shipment and get a tracking number
            As a shipper, I want to book a shipment and receive a tracking number, so that I and my customer can follow the parcel.

            ### Acceptance criteria
            Scenario: Shipment booked
              Given sender "Acme Ltd" and recipient "Jane Doe"
              When I book a shipment from "Mumbai" to "Pune" with 3 transit days
              Then I receive a tracking number like "LG0000000001"
              And the shipment status is CREATED at "Mumbai"
              And the estimated delivery date is 3 days from today

            Scenario: Missing details
              When I book a shipment without a destination
              Then the booking is rejected with "Destination is required"

            ## Story 2: Track a shipment
            As a recipient, I want to enter a tracking number and see the current status, location and history, so that I know when my parcel will arrive.

            ### Acceptance criteria
            Scenario: Known tracking number
              Given shipment LG0000000001 is IN_TRANSIT at "Lonavala hub"
              When I track LG0000000001
              Then I see status IN_TRANSIT, location "Lonavala hub", the estimated delivery date and all scan events

            Scenario: Malformed tracking number
              When I track "ABC123"
              Then I see "Tracking number must be LG followed by 10 digits"

            Scenario: Unknown tracking number
              When I track LG9999999999
              Then I see "Shipment not found: LG9999999999"

            ## Story 3: Record scan events in the right order
            As a dispatcher, I want each scan to move the shipment through the allowed statuses only, so that tracking data stays trustworthy.

            ### Acceptance criteria
            Scenario: Normal journey
              Given a CREATED shipment
              When it is scanned PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY and DELIVERED
              Then each scan is accepted and the history lists 5 events

            Scenario: Skipped status
              Given a CREATED shipment
              When it is scanned DELIVERED
              Then the scan is rejected with "Cannot move shipment from CREATED to DELIVERED"

            Scenario: Delivered is final
              Given a DELIVERED shipment
              When another scan arrives
              Then the scan is rejected

            ## Story 4: Delivery exceptions and delays
            As a customer service agent, I want exceptions to carry a reason and to push the ETA, so that customers get honest delivery dates.

            ### Acceptance criteria
            Scenario: Exception needs a reason
              When a dispatcher records an EXCEPTION without a note
              Then it is rejected with "An exception scan needs a note explaining the problem"

            Scenario: Exception delays the ETA
              Given the ETA is 3 days from today
              When an EXCEPTION "Address not found" is recorded
              Then the ETA moves out by 2 days

            Scenario: Overdue shipment
              Given a shipment not yet delivered whose ETA has passed
              Then it is reported as delayed

            ## Assumptions
            - Tracking numbers are "LG" followed by 10 digits and are unique.
            - Times are recorded in UTC; ETA is a calendar date.
            - Carrier integrations and notifications are out of scope for this release.
            """;

    private static String generic(StageDomain domain, String requirement) {
        String title = StageSample.featureTitle(requirement);
        String lowerTitle = title.substring(0, 1).toLowerCase(Locale.ROOT) + title.substring(1);
        String customer = domain.customerRole();
        String staff = domain.staffRole();
        String approver = domain.approver();
        String control = domain.control();
        return """
                # Feature: %s
                Domain: %s

                ## Story 1: Submit a %s request
                As a %s, I want to raise a %s request online, so that it is handled quickly without paperwork.

                ### Acceptance criteria
                Scenario: Valid request is submitted
                  Given I am a signed-in %s
                  When I submit a %s request with all required details
                  Then the request is saved with status SUBMITTED and a reference number
                  And I see a confirmation with the reference number

                Scenario: Missing details
                  When I submit the request without details
                  Then it is rejected with "Details are required"

                ## Story 2: Review and approve
                As a %s, I want %s to approve or reject each request, so that we meet %s.

                ### Acceptance criteria
                Scenario: Approve a request
                  Given a SUBMITTED request raised by someone else
                  When I approve it
                  Then its status becomes APPROVED and my name is recorded

                Scenario: Cannot approve my own request
                  Given a SUBMITTED request that I raised
                  When I try to approve it
                  Then it is rejected with "Requester cannot approve their own request"

                Scenario: Reject needs a reason
                  When I reject a request without a reason
                  Then it is rejected with "A reason is required to reject a request"

                ## Story 3: Audit trail
                As a %s, I want every action on a %s request to be logged, so that we can answer audits and disputes.

                ### Acceptance criteria
                Scenario: Actions are logged
                  Given a request was submitted and approved
                  When I open its audit trail
                  Then I see both actions with who did them and when

                ## Assumptions
                - Requirement as given: "%s".
                - Only SUBMITTED requests can be approved or rejected; decisions are final.
                - Details are limited to 500 characters.
                """.formatted(title, domain.label(), lowerTitle, customer, lowerTitle, customer, lowerTitle,
                staff, approver, control, staff, lowerTitle,
                requirement == null ? "" : requirement.strip().replace('"', '\''));
    }
}
