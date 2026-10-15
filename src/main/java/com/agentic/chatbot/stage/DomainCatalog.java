package com.agentic.chatbot.stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The business domains offered in the stage bots' dropdown, grouped by industry. Each has the customer and staff
 * roles and the expertise the persona prompts use, keywords that match free text ("hospital" gives Healthcare),
 * and sample requirements for the UI. Any other text is still accepted as a custom domain by {@link StageDomain}.
 */
public final class DomainCatalog {

    /**
     * One domain.
     *
     * @param keywords lower-case words that select this domain when they appear in a typed domain id
     * @param samples  example requirements shown as chips in the UI
     */
    public record Domain(String label, String group, String customerRole, String staffRole, String expertise,
                         List<String> keywords, List<String> samples) {

        /** Same id {@link StageDomain#from(String)} derives from the label. */
        public String id() {
            return slug(label);
        }
    }

    private static final List<Domain> DOMAINS = new ArrayList<>();

    static {
        String g = "Financial services";
        add("Banking", g, "bank customer", "bank operations officer",
                "KYC/AML checks, transaction limits, maker-checker approval, audit trails, BigDecimal money, idempotent payments",
                List.of("bank"), "Fund transfer between accounts", "Loan application approval", "Credit card limit increase request");
        add("Capital Markets / Trading", g, "trader", "risk manager",
                "orders and executions, positions, market data, pre-trade risk limits, settlement (T+1) and MiFID/SEC reporting",
                List.of("trading", "capital-market", "brokerage", "securities", "stock"),
                "Equity order placement", "Pre-trade risk limit check", "Trade settlement exception handling");
        add("Payments / FinTech", g, "payer", "payments operations analyst",
                "payment initiation, card and wallet payments, PCI-DSS, fraud screening, idempotency keys and reconciliation",
                List.of("payment", "fintech", "wallet"), "Peer-to-peer payment", "Refund request processing", "Daily payment reconciliation");
        add("Insurance", g, "policyholder", "claims handler",
                "policies, premiums, claims, underwriting rules, fraud checks and regulatory reporting",
                List.of("insurance", "insurer", "claim"), "Motor insurance claim submission", "Policy renewal quote", "Claim payout approval");
        add("Lending / Mortgage", g, "borrower", "loan underwriter",
                "loan origination, credit scoring, affordability and LTV rules, amortisation schedules and arrears handling",
                List.of("lending", "mortgage", "loan", "credit"), "Mortgage application", "Loan repayment schedule", "Arrears collection workflow");
        add("Accounting", g, "accountant", "finance controller",
                "general ledger, double-entry bookkeeping, accounts payable/receivable, period close, tax and audit trails",
                List.of("accounting", "ledger", "bookkeeping", "finance"), "Invoice approval", "Month-end close checklist", "Expense claim reimbursement");

        g = "Healthcare & life sciences";
        add("Healthcare", g, "patient", "clinician",
                "patient records, consent, HIPAA/GDPR privacy, appointments, clinical safety checks and audit trails",
                List.of("health", "hospital", "clinic", "patient", "medical"),
                "Patient appointment booking", "Prescription refill request", "Lab result release approval");
        add("Pharma / Life Sciences", g, "trial participant", "clinical research associate",
                "clinical trials, GxP and 21 CFR Part 11 compliance, batch records, pharmacovigilance and drug safety reporting",
                List.of("pharma", "life-science", "biotech", "clinical-trial", "drug"),
                "Adverse event reporting", "Clinical trial enrolment", "Batch release approval");
        add("Medical Devices", g, "device user", "quality engineer",
                "device registration, ISO 13485 and IEC 62304, complaint handling, CAPA and post-market surveillance",
                List.of("medical-device", "device", "medtech"), "Device complaint intake", "Firmware release approval", "Field safety notice");

        g = "Commerce";
        add("Retail", g, "shopper", "store manager",
                "catalogue, carts, orders, payments, returns, inventory and promotions",
                List.of("retail", "store", "shop"), "Product return request", "Store stock replenishment", "Loyalty points redemption");
        add("E-commerce", g, "online shopper", "e-commerce operations manager",
                "product catalogue, cart and checkout, payments, order fulfilment, returns, promotions and customer reviews",
                List.of("commerce", "ecommerce", "marketplace", "online-store"),
                "Shopping cart checkout", "Order cancellation", "Discount coupon application");
        add("CRM / Sales", g, "prospect", "sales representative",
                "leads, opportunities, pipelines, quotes, territories, forecasting and customer data privacy",
                List.of("crm", "sales"), "Lead qualification", "Sales quote approval", "Opportunity stage update");
        add("Marketing / AdTech", g, "advertiser", "campaign manager",
                "campaigns, audiences and segmentation, ad bidding, attribution, consent (GDPR/CCPA) and budget pacing",
                List.of("marketing", "adtech", "advertis", "campaign"), "Campaign budget approval", "Audience segment creation", "Email unsubscribe handling");

        g = "Logistics & mobility";
        add("Logistics / Supply Chain", g, "shipper", "dispatcher",
                "shipment lifecycle and status transitions, tracking numbers, ETAs and SLAs, proof of delivery, exception handling",
                List.of("logist", "supply-chain", "shipping", "warehouse", "freight"),
                "Shipment tracking", "Delivery slot booking", "Warehouse stock transfer request");
        add("Transportation", g, "passenger", "fleet controller",
                "routes, timetables, ticketing, fleet maintenance, driver hours rules and safety incidents",
                List.of("transport", "transit", "fleet", "railway", "rail", "bus"), "Ticket purchase", "Vehicle maintenance request", "Route disruption notice");
        add("Aviation", g, "passenger", "airline operations controller",
                "flight schedules, reservations and PNRs, check-in, crew rostering, MRO maintenance and EASA/FAA safety rules",
                List.of("aviation", "airline", "airport", "flight"), "Flight check-in", "Aircraft maintenance work order", "Crew roster change");
        add("Automotive", g, "car owner", "service advisor",
                "vehicle configuration, dealer orders, VIN tracking, service and warranty claims, recalls and connected-car data",
                List.of("automotive", "car", "vehicle", "dealer"), "Service appointment booking", "Warranty claim submission", "Vehicle recall notification");
        add("Travel", g, "traveller", "travel agent",
                "bookings, fares, availability, cancellations, refunds and passenger data",
                List.of("travel", "tourism", "tour-operator"), "Flight booking cancellation", "Holiday package booking", "Travel expense approval");
        add("Hospitality / Hotels", g, "guest", "front-desk manager",
                "reservations, room inventory and rates, check-in/out, housekeeping, loyalty programmes and payments",
                List.of("hospitality", "hotel", "resort"), "Hotel reservation change", "Room upgrade request", "Late checkout approval");
        add("Food & Restaurants", g, "diner", "restaurant manager",
                "menus, table reservations, orders and kitchen tickets, delivery, allergens, food safety and tips",
                List.of("food", "restaurant", "dining", "delivery-app"), "Table reservation", "Online food order", "Allergen information request");

        g = "Industry & energy";
        add("Manufacturing", g, "plant operator", "production supervisor",
                "work orders, bills of materials, quality inspections, machine downtime and traceability",
                List.of("manufactur", "factory", "plant", "production"), "Work order release", "Quality inspection sign-off", "Machine maintenance request");
        add("Energy / Utilities", g, "utility customer", "grid operations engineer",
                "metering and billing, tariffs, outages, smart meters, grid balancing and regulatory reporting",
                List.of("energy", "utilit", "electric", "power", "water", "gas-utility"), "Meter reading submission", "Outage report", "Tariff change request");
        add("Oil & Gas", g, "field engineer", "operations supervisor",
                "wells and assets, production volumes, HSE incidents, permits to work, pipeline integrity and maintenance",
                List.of("oil", "petroleum", "upstream", "refinery"), "Permit to work approval", "HSE incident report", "Pipeline inspection schedule");
        add("Construction", g, "client", "site manager",
                "projects and phases, bids, change orders, site safety, subcontractors, materials and inspections",
                List.of("construction", "building", "contractor"), "Change order approval", "Site safety inspection", "Subcontractor invoice");
        add("Agriculture", g, "farmer", "agronomist",
                "fields and crops, planting and harvest, livestock, weather, irrigation, subsidies and traceability",
                List.of("agri", "farm", "crop", "livestock"), "Crop planting plan", "Harvest yield record", "Irrigation schedule request");
        add("IoT", g, "device owner", "IoT platform operator",
                "device provisioning, telemetry ingestion, firmware updates (OTA), alerts and rules, device security",
                List.of("iot", "internet-of-things", "sensor", "telemetry"), "Device provisioning", "Sensor alert rule", "Firmware update rollout");

        g = "Technology, media & telecom";
        add("Telecom", g, "subscriber", "support agent",
                "plans, SIM and number provisioning, usage rating, billing and service outages",
                List.of("telecom", "telco", "mobile-operator", "isp"), "SIM swap request", "Plan upgrade", "Outage ticket escalation");
        add("Media / Entertainment", g, "viewer", "content manager",
                "content catalogue, rights and licensing windows, subscriptions, streaming, royalties and age ratings",
                List.of("media", "entertainment", "streaming", "broadcast", "music", "film"), "Subscription sign-up", "Content licence renewal", "Parental control setting");
        add("Gaming", g, "player", "live-ops manager",
                "player accounts, matchmaking, in-game purchases, leaderboards, anti-cheat and age-appropriate design",
                List.of("gaming", "game", "esports"), "In-game purchase", "Leaderboard update", "Player ban appeal");
        add("Social Media", g, "member", "trust & safety moderator",
                "profiles, posts and feeds, follows, messaging, content moderation, reporting abuse and privacy settings",
                List.of("social", "community", "forum"), "Post reporting and moderation", "Friend request", "Account privacy settings");
        add("Cybersecurity", g, "security analyst", "SOC manager",
                "identity and access, vulnerabilities, incidents and alerts, SIEM, least privilege, ISO 27001/SOC 2 controls",
                List.of("cyber", "security", "infosec", "soc"), "Access request approval", "Security incident triage", "Vulnerability remediation tracking");

        g = "Public sector & services";
        add("Government / Public Sector", g, "citizen", "case officer",
                "applications, eligibility rules, case management, accessibility, data protection and audit",
                List.of("government", "public-sector", "council", "municipal", "citizen"), "Permit application", "Benefit eligibility check", "Document verification request");
        add("Defense", g, "service member", "logistics officer",
                "asset and inventory tracking, security clearances, mission planning, need-to-know access and audit",
                List.of("defense", "defence", "military"), "Equipment requisition", "Clearance verification", "Maintenance readiness report");
        add("Education / EdTech", g, "student", "course administrator",
                "enrolment, courses, grading, timetables, safeguarding and student privacy",
                List.of("educat", "edtech", "school", "university", "learning", "course"), "Course enrolment request", "Exam grade appeal", "Scholarship application");
        add("Legal", g, "client", "paralegal",
                "matters and cases, conflict checks, documents and e-discovery, billing hours, deadlines and confidentiality",
                List.of("legal", "law", "attorney", "court"), "New matter intake with conflict check", "Court deadline reminder", "Time entry approval");
        add("Real Estate", g, "tenant", "property manager",
                "listings, viewings, leases and rent, maintenance requests, deposits and property compliance",
                List.of("real-estate", "property", "realty", "rental"), "Rental application", "Maintenance request", "Lease renewal");
        add("HR / Payroll", g, "employee", "HR administrator",
                "onboarding, leave and absence, timesheets, payroll and tax deductions, performance reviews and data privacy",
                List.of("hr", "payroll", "human-resource", "recruit", "talent"), "Leave request approval", "Employee onboarding", "Payroll adjustment");
        add("Non-profit", g, "donor", "programme coordinator",
                "donations and gift aid, campaigns, volunteers, grants, beneficiaries and transparent reporting",
                List.of("non-profit", "nonprofit", "charity", "ngo"), "Online donation", "Volunteer shift sign-up", "Grant application review");
        add("Sports / Fitness", g, "member", "club manager",
                "memberships, class bookings, trainers, fixtures and results, wearables data and health waivers",
                List.of("sport", "fitness", "gym", "club"), "Class booking", "Membership freeze request", "Match result submission");
    }

    private DomainCatalog() {
    }

    private static void add(String label, String group, String customer, String staff, String expertise,
                            List<String> keywords, String... samples) {
        DOMAINS.add(new Domain(label, group, customer, staff, expertise, keywords, List.of(samples)));
    }

    public static List<Domain> all() {
        return List.copyOf(DOMAINS);
    }

    /** Domains grouped for the dropdown's optgroups, in display order. */
    public static Map<String, List<Domain>> groups() {
        Map<String, List<Domain>> out = new LinkedHashMap<>();
        DOMAINS.forEach(d -> out.computeIfAbsent(d.group(), k -> new ArrayList<>()).add(d));
        return out;
    }

    public static List<String> labels() {
        return DOMAINS.stream().map(Domain::label).toList();
    }

    /**
     * The catalog domain for a label or id: an exact label/id match first, then the first domain whose keyword
     * appears in the id (so "City hospital" gives Healthcare).
     */
    public static Optional<Domain> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String id = slug(value);
        for (Domain d : DOMAINS) {
            if (d.id().equals(id) || d.label().equalsIgnoreCase(value.strip())) {
                return Optional.of(d);
            }
        }
        String padded = "-" + id + "-";
        for (Domain d : DOMAINS) {
            for (String k : d.keywords()) {
                if (k.length() <= 3 ? padded.contains("-" + k + "-") : id.contains(k)) {
                    return Optional.of(d);
                }
            }
        }
        return Optional.empty();
    }

    static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    // ---- Business Analyst starter requirements ------------------------------------------------------------

    /** Hand-written requirements keyed by domain id; every other domain gets {@link #templateRequirement}. */
    private static final Map<String, String> REQUIREMENTS = new LinkedHashMap<>();

    static {
        REQUIREMENTS.put("banking", """
                Fund transfer between accounts (online banking)
                - A logged-in customer transfers money from one of their own accounts to another account (internal or to a saved beneficiary).
                - The source account must have sufficient available balance; amount must be greater than zero and in the account currency.
                - Limits: single transfer up to 50,000; daily limit 100,000 per customer. Transfers above 10,000 need a one-time password (OTP) sent to the registered mobile.
                - Transfers above the daily limit or to a blocked/closed account are rejected with a clear reason.
                - Every attempt (success or failure) is written to an immutable audit trail with timestamp, user, accounts, amount, channel and outcome.
                - The customer gets a receipt with a unique reference number; repeating the same request (same idempotency key) must not debit twice.
                - Suspicious transfers (new beneficiary + large amount) are flagged for review by a bank operations officer (maker-checker).""");
        REQUIREMENTS.put("logistics-supply-chain", """
                Shipment tracking
                - A shipper creates a shipment with origin, destination, weight, service level (standard / express) and receives a unique tracking number.
                - A shipment moves through the statuses CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, or EXCEPTION; invalid jumps (e.g. CREATED to DELIVERED) are rejected.
                - Each status change records a timestamp, location and the scan source (driver app, hub scanner).
                - Anyone with the tracking number can see the current status, the full event history and the estimated delivery date (ETA).
                - If a shipment is delayed beyond its SLA or delivery fails, it moves to EXCEPTION and a dispatcher is notified.
                - Delivery requires proof of delivery (recipient name and signature or photo).
                - The shipper can cancel a shipment only before pick-up.""");
        REQUIREMENTS.put("healthcare", """
                Patient appointment booking
                - A patient searches for a doctor by speciality and date, sees the free time slots, and books one slot.
                - A slot can be booked by only one patient; double booking and booking in the past are rejected.
                - The patient can reschedule or cancel up to 24 hours before the appointment; later changes need clinic approval.
                - The patient receives a confirmation and a reminder 24 hours before the visit by SMS or e-mail.
                - Patient data is private: only the patient and the treating clinician can view the appointment details (HIPAA/GDPR); consent is recorded.
                - Clinicians can mark an appointment as completed or no-show; every change is written to an audit trail.""");
        REQUIREMENTS.put("insurance", """
                Motor insurance claim submission
                - A policyholder with an active policy reports an accident: date, location, description, photos and police report number.
                - The claim is rejected if the policy was not active on the date of loss or the claim is filed more than 30 days later.
                - A claims handler reviews the claim, may request more documents, and approves or declines it with a reason.
                - Claims above 5,000 need a second approver; suspected fraud (duplicate claim, very recent policy) is flagged.
                - The policyholder can track the claim status and receives a notification at each change; all decisions are audited.""");
        REQUIREMENTS.put("retail", """
                Store stock replenishment
                - A store manager sees the stock level of each product and a low-stock list (below the reorder point).
                - The manager raises a replenishment order for a product with quantity, supplier and requested delivery date.
                - Quantity must be positive and within the supplier's minimum and maximum order size.
                - Orders above 10,000 value need regional manager approval.
                - Receiving goods updates the stock level; mismatches against the order are recorded as discrepancies.
                - All stock changes are traceable by user and time.""");
        REQUIREMENTS.put("e-commerce", """
                Online checkout and order placement
                - A shopper reviews the cart, enters shipping address, picks a delivery option and pays by card or wallet.
                - Stock is reserved when the order is placed; out-of-stock items block checkout with a clear message.
                - Coupons are validated (expiry, minimum basket, one per order) and the total shows items, discount, tax and shipping.
                - Payment failures keep the cart intact and do not create a paid order; duplicate submissions create only one order.
                - The shopper gets an order confirmation with a number and can cancel until the order is shipped.""");
        REQUIREMENTS.put("telecom", """
                Mobile plan change and top-up
                - A subscriber views the current plan, usage and available plans, and requests a plan change effective now or at the next bill cycle.
                - A change is rejected while the account has an unpaid bill above the credit limit or an active contract lock-in.
                - Prorated charges or credits are shown before the subscriber confirms.
                - Top-ups between 5 and 500 are accepted by card; the balance updates immediately and a receipt is sent by SMS.
                - Support agents can see the request history; every plan change is audited.""");
        REQUIREMENTS.put("education-edtech", """
                Course enrolment
                - A student browses courses, checks prerequisites and seat availability, and enrols in up to 6 courses per term.
                - Enrolment is rejected if prerequisites are not met, the course is full (the student may join a waiting list), or the schedule clashes.
                - Students can drop a course until the end of week 2 without penalty.
                - The course administrator can open or close enrolment and override a prerequisite with a recorded reason.
                - Students receive a confirmation, and student records are visible only to the student and authorised staff.""");
        REQUIREMENTS.put("hr-payroll", """
                Leave request and approval
                - An employee requests leave (type, start and end date, reason) and sees the remaining balance for that leave type.
                - A request is rejected if it exceeds the balance, overlaps another approved leave, or has an end date before the start date.
                - The manager approves or rejects with a comment; requests of more than 10 days also need HR approval.
                - Approved leave updates the balance and the team calendar; the employee can cancel before the start date.
                - All decisions are audited and personal data is visible only to the employee, the manager and HR.""");
        REQUIREMENTS.put("travel", """
                Flight and hotel booking
                - A traveller searches flights by origin, destination, dates and passengers, picks an option and optionally adds a hotel.
                - Passenger names must match their travel documents; infants cannot be booked alone.
                - A seat is held for 15 minutes while payment is made; after that the hold expires.
                - Cancellation is free within 24 hours of booking, afterwards the fare rules fee applies.
                - The traveller gets an itinerary with a booking reference; a travel agent can modify or cancel on request, with every change audited.""");
    }

    /** The Business Analyst starter requirement for a catalog domain: hand-written, else a template built from its data. */
    public static String requirement(Domain d) {
        String own = REQUIREMENTS.get(d.id());
        return own != null ? own : templateRequirement(d);
    }

    /**
     * The starter requirement for any domain text: the catalog's own text when it matches a catalog domain,
     * otherwise a neutral template for a custom domain.
     */
    public static String requirementFor(String domainText) {
        return find(domainText).map(DomainCatalog::requirement).orElseGet(() -> genericRequirement(domainText));
    }

    /** Neutral template for a domain that is not in the catalog. */
    public static String genericRequirement(String domainName) {
        String name = domainName == null || domainName.isBlank() ? "the business" : domainName.strip();
        return """
                Service request approval (%s)
                - A user submits a request with the required details (who, what, when, amount or quantity) and receives a unique reference number.
                - Mandatory fields and valid ranges are checked; invalid requests are rejected with a clear reason.
                - A reviewer approves or rejects the request with a comment; the person who raised it cannot approve it.
                - Limits and exceptions follow the %s business rules; requests over the limit need a second approval.
                - The requester can track the status and cancel the request until it is approved.
                - Every action is recorded in an audit trail with user, time and outcome.""".formatted(name, name);
    }

    private static String templateRequirement(Domain d) {
        String main = d.samples().isEmpty() ? "Service request approval" : d.samples().get(0);
        String others = d.samples().size() > 1 ? String.join("; ", d.samples().subList(1, d.samples().size())) : "";
        String text = """
                %s (%s)
                - A %s starts the "%s" journey by entering the required details and receives a unique reference number.
                - Mandatory fields, valid ranges and domain limits are validated; invalid requests are rejected with a clear reason.
                - A %s reviews the request and approves, rejects or asks for more information, with a comment; the person who raised it cannot approve it.
                - Business rules to respect: %s.
                - The %s can follow the status and is notified at each change; cancelling is allowed until approval.
                - Every action is written to an audit trail with user, time and outcome.""".formatted(
                main, d.label(), d.customerRole(), main.toLowerCase(Locale.ROOT), d.staffRole(), d.expertise(), d.customerRole());
        return others.isEmpty() ? text : text + "\n- Related flows to consider later: " + others + ".";
    }}
