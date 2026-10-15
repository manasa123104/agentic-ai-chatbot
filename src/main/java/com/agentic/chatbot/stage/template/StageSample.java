package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which offline template fits a requirement, story or piece of code, and the feature name used for classes.
 */
public enum StageSample {
    FUND_TRANSFER("FundTransfer", "com.bank.transfer"),
    SHIPMENT_TRACKING("ShipmentTracking", "com.logistics.tracking"),
    GENERIC(null, null);

    private static final Pattern FEATURE_LINE = Pattern.compile("^\\s*#*\\s*Feature:\\s*(.+)$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "to", "for", "of", "and", "or", "in", "on", "with", "between", "from", "by", "into",
            "i", "we", "want", "wants", "need", "needs", "should", "able", "be", "can", "as", "so", "that", "this",
            "user", "users", "customer", "customers", "system", "allow", "allows", "let", "lets", "my", "our", "their",
            "feature", "story", "stories", "requirement", "build", "implement", "please", "bank", "banking",
            "logistics", "app", "application", "page", "screen", "module", "via", "using", "is", "are", "it");

    private final String feature;
    private final String packageName;

    StageSample(String feature, String packageName) {
        this.feature = feature;
        this.packageName = packageName;
    }

    /**
     * Specific words ("fund transfer", "shipment") pick a sample in any domain; generic words ("transfer",
     * "tracking") only count inside the matching domain, so "expense tracking" for a bank stays generic.
     */
    public static StageSample detect(String text, StageDomain domain) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.contains("fundtransferservice") || t.contains("fund transfer") || t.contains("transfer money")
                || t.contains("transfer funds")) {
            return FUND_TRANSFER;
        }
        if (t.contains("shipmenttrackingservice") || t.contains("shipment") || t.contains("parcel")) {
            return SHIPMENT_TRACKING;
        }
        if (domain == StageDomain.BANKING && t.matches("(?s).*\\btransfers?\\b.*")) {
            return FUND_TRANSFER;
        }
        if (domain == StageDomain.LOGISTICS && t.matches("(?s).*\\b(track|tracking|consignment)\\b.*")) {
            return SHIPMENT_TRACKING;
        }
        return GENERIC;
    }

    /** PascalCase feature name, e.g. "Loan application approval" gives "LoanApplication". */
    public String featureName(String text) {
        return feature != null ? feature : genericName(text);
    }

    public String packageName(StageDomain domain, String text) {
        return packageName != null ? packageName : domain.basePackage() + "." + featureName(text).toLowerCase(Locale.ROOT);
    }

    public static String genericName(String text) {
        List<String> words = keyWords(featureTitle(text));
        if (words.isEmpty()) {
            return "ServiceRequest";
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words.subList(0, Math.min(2, words.size()))) {
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        String name = sb.toString();
        return Character.isDigit(name.charAt(0)) ? "F" + name : name;
    }

    /** Human title: the "Feature:" line of stories, else the first line of the requirement. */
    public static String featureTitle(String text) {
        if (text == null || text.isBlank()) {
            return "Service request";
        }
        Matcher m = FEATURE_LINE.matcher(text);
        String line = m.find() ? m.group(1) : text.strip().lines().findFirst().orElse("");
        line = line.replaceAll("[#*`_]", "").strip();
        Matcher want = Pattern.compile("(?i)\\bI want(?: to)?\\s+(.*?)(?:,? so that.*)?$").matcher(line);
        if (want.find()) {
            line = want.group(1);
        }
        line = line.replaceAll("[.]+$", "").strip();
        if (line.length() > 70) {
            line = line.substring(0, 70).replaceAll("\\s+\\S*$", "");
        }
        return line.isEmpty() ? "Service request" : Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    static List<String> keyWords(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}
