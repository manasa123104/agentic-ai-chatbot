package com.agentic.chatbot.stage;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Business domain the stage bots role-play in. Banking and Logistics are built in (they have dedicated offline
 * samples); any other text the user types becomes a custom domain with neutral personas, roles and packages.
 * {@link #from(String)} always returns the {@link #BANKING} / {@link #LOGISTICS} singletons for those two,
 * so identity comparisons with them are safe.
 */
public final class StageDomain {

    public static final StageDomain BANKING = new StageDomain("banking", "Banking", "com.bank",
            "a bank (retail and corporate banking: accounts, payments, cards, loans)",
            "bank customer", "bank operations officer",
            "KYC/AML checks, transaction limits, maker-checker approval, audit trails, BigDecimal money, idempotent payments",
            "a second bank officer (maker-checker)", "the bank's four-eyes control and audit requirements",
            "maker-checker (four-eyes) control: the person who raised a request cannot approve it", "BNK");

    public static final StageDomain LOGISTICS = new StageDomain("logistics", "Logistics", "com.logistics",
            "a logistics company (parcel and freight: shipments, warehouses, carriers, last-mile delivery)",
            "shipper", "dispatcher",
            "shipment lifecycle and status transitions, tracking numbers, ETAs and SLAs, proof of delivery, exception handling",
            "a dispatcher", "delivery SLAs and proof of handling",
            "dispatcher sign-off: the person who raised a request cannot approve it", "LOG");

    /** The dropdown's domain labels (see {@link DomainCatalog}); any other text is accepted as a custom domain. */
    public static final List<String> SUGGESTIONS = DomainCatalog.labels();

    private static final int MAX_LABEL = 60;

    private final String id;
    private final String label;
    private final String basePackage;
    private final String organisation;
    private final String customerRole;
    private final String staffRole;
    private final String expertise;
    private final String approver;
    private final String control;
    private final String checkerRule;
    private final String refPrefix;

    private StageDomain(String id, String label, String basePackage, String organisation, String customerRole,
                        String staffRole, String expertise, String approver, String control, String checkerRule,
                        String refPrefix) {
        this.id = id;
        this.label = label;
        this.basePackage = basePackage;
        this.organisation = organisation;
        this.customerRole = customerRole;
        this.staffRole = staffRole;
        this.expertise = expertise;
        this.approver = approver;
        this.control = control;
        this.checkerRule = checkerRule;
        this.refPrefix = refPrefix;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String basePackage() {
        return basePackage;
    }

    public String organisation() {
        return organisation;
    }

    public String customerRole() {
        return customerRole;
    }

    public String staffRole() {
        return staffRole;
    }

    public String expertise() {
        return expertise;
    }

    /** Who approves requests in the generic workflow stories. */
    public String approver() {
        return approver;
    }

    /** What the approval step protects, used in story wording. */
    public String control() {
        return control;
    }

    /** Business-rule comment for the four-eyes check in the generic workflow code. */
    public String checkerRule() {
        return checkerRule;
    }

    /** Prefix of generated reference numbers, e.g. BNK-000001. */
    public String refPrefix() {
        return refPrefix;
    }

    public boolean builtIn() {
        return this == BANKING || this == LOGISTICS;
    }

    /** Blank values fall back to banking; "logistics"-like values give logistics; anything else is a custom domain. */
    public static StageDomain from(String value) {
        if (value == null || value.isBlank()) {
            return BANKING;
        }
        String label = value.replaceAll("[\\p{Cntrl}<>\"`]", " ").replaceAll("\\s+", " ").strip();
        if (label.length() > MAX_LABEL) {
            label = label.substring(0, MAX_LABEL).strip();
        }
        String v = label.toLowerCase(Locale.ROOT);
        if (v.equals("banking") || v.equals("bank") || v.startsWith("banking ")) {
            return BANKING;
        }
        if (v.startsWith("logist") || v.contains("shipping") || v.contains("supply chain")) {
            return LOGISTICS;
        }
        String id = v.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (id.isEmpty()) {
            return BANKING;
        }
        String[] roles = DomainCatalog.find(label)
                .map(d -> new String[]{d.customerRole(), d.staffRole(), d.expertise()})
                .orElse(null);
        String pkgWord = id.replaceAll("[^a-z0-9]", "");
        if (pkgWord.length() > 20) {
            pkgWord = pkgWord.substring(0, 20);
        }
        if (Character.isDigit(pkgWord.charAt(0))) {
            pkgWord = "d" + pkgWord;
        }
        String letters = id.replaceAll("[^a-z]", "").toUpperCase(Locale.ROOT) + "XXX";
        String display = Character.toUpperCase(label.charAt(0)) + label.substring(1);
        return new StageDomain(id, display, "com." + pkgWord,
                "an organisation in the " + display + " domain",
                roles == null ? "customer" : roles[0],
                roles == null ? "operations officer" : roles[1],
                roles == null
                        ? "the " + display + " domain's business rules, regulations, data validation and audit requirements"
                        : roles[2],
                "a supervisor", "the " + display + " approval and audit requirements",
                "four-eyes approval: the person who raised a request cannot approve it",
                letters.substring(0, 3));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StageDomain d && d.id.equals(id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return label;
    }
}
