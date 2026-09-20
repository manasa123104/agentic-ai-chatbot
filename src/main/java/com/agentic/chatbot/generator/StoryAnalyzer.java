package com.agentic.chatbot.generator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infers screen type, domain, feature name, and redirect target from a user story.
 */
public final class StoryAnalyzer {

    public enum ScreenType {
        LOGIN, REGISTER, DASHBOARD, FORM, LIST, GENERIC
    }

    public enum Domain {
        LOGIN, REGISTER, APPOINTMENT, ECOMMERCE, CATALOG, LANDING, HR, SUPPORT, FINANCE, DASHBOARD, GENERIC
    }

    public record Analysis(
            ScreenType type,
            Domain domain,
            String featureName,
            String packageName,
            String classPrefix,
            String pagePath,
            String redirectPath,
            String headline,
            String[] fieldLabels
    ) {
    }

    private StoryAnalyzer() {
    }

    public static Analysis analyze(String userStory) {
        String story = userStory == null ? "" : userStory.toLowerCase(Locale.ROOT);
        Domain domain = detectDomain(story);
        ScreenType type = detectType(story, domain);

        String feature = extractFeatureName(userStory, type, domain);
        String classPrefix = toPascalCase(feature);
        String packageName = "com.generated." + feature.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String pagePath = "/" + feature.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        String redirectPath = extractRedirectPath(userStory, type, pagePath);
        String headline = buildHeadline(domain, classPrefix);
        String[] fields = fieldLabelsFor(domain);
        return new Analysis(type, domain, feature, packageName, classPrefix, pagePath, redirectPath, headline, fields);
    }

    private static Domain detectDomain(String story) {
        if (story.contains("login") || story.contains("sign in") || story.contains("authenticate") || story.contains("gmail") || story.contains("sign-in")) {
            return Domain.LOGIN;
        }
        if (story.contains("register") || story.contains("sign up") || story.contains("signup") || story.contains("create an account")) {
            return Domain.REGISTER;
        }
        if (story.contains("appointment") || story.contains("booking") || story.contains("schedule") || story.contains("doctor") || story.contains("hospital") || story.contains("clinic")) {
            return Domain.APPOINTMENT;
        }
        if (story.contains("cart") || story.contains("checkout") || story.contains("payment") || story.contains("order") || story.contains("shop") || story.contains("buy")) {
            return Domain.ECOMMERCE;
        }
        if (story.contains("catalog") || story.contains("product list") || story.contains("browse") || story.contains("search")) {
            return Domain.CATALOG;
        }
        if (story.contains("leave") || story.contains("hr") || story.contains("employee") || story.contains("onboarding") || story.contains("recruit")) {
            return Domain.HR;
        }
        if (story.contains("ticket") || story.contains("support") || story.contains("contact") || story.contains("feedback") || story.contains("complaint")) {
            return Domain.SUPPORT;
        }
        if (story.contains("bank") || story.contains("transfer") || story.contains("insurance") || story.contains("loan") || story.contains("finance")) {
            return Domain.FINANCE;
        }
        if (story.contains("dashboard") || story.contains("workspace") || story.contains("portal")) {
            return Domain.DASHBOARD;
        }
        if (story.contains("landing") || story.contains("homepage") || story.contains("home page")
                || story.contains("marketing") || story.contains("saas") || story.contains("pricing")
                || story.contains("company") || story.contains("website") || story.contains("big site")
                || story.contains("full site") || story.contains("multi-page") || story.contains("corporate")
                || story.contains("restaurant") || story.contains("travel") || story.contains("hotel")
                || story.contains("cafe") || story.contains("café") || story.contains("tourism")
                || story.contains("resort") || story.contains("gym") || story.contains("fitness")
                || story.contains("music") || story.contains("piano") || story.contains("guitar")
                || story.contains("concert") || story.contains("choir") || story.contains("orchestra")
                || story.contains("wedding") || story.contains("bridal") || story.contains("pet")
                || story.contains("bookstore") || story.contains("library") || story.contains("garden")
                || story.contains("nursery") || story.contains("florist")
                || story.contains("gaming") || story.contains("esport") || story.contains("planetarium")
                || story.contains("astronomy") || story.contains("cleaning") || story.contains("violin")
                || story.contains("musician")) {
            return Domain.LANDING;
        }
        return Domain.GENERIC;
    }

    private static ScreenType detectType(String story, Domain domain) {
        return switch (domain) {
            case LOGIN -> ScreenType.LOGIN;
            case REGISTER -> ScreenType.REGISTER;
            case DASHBOARD -> ScreenType.DASHBOARD;
            case CATALOG -> ScreenType.LIST;
            case APPOINTMENT, ECOMMERCE, HR, SUPPORT, FINANCE -> ScreenType.FORM;
            case LANDING, GENERIC -> {
                if (story.contains("list") || story.contains("table") || story.contains("catalog")) {
                    yield ScreenType.LIST;
                }
                if (story.contains("form") || story.contains("submit") || story.contains("book") || story.contains("apply")) {
                    yield ScreenType.FORM;
                }
                yield ScreenType.GENERIC;
            }
        };
    }

    private static String buildHeadline(Domain domain, String classPrefix) {
        return switch (domain) {
            case LOGIN -> "Sign in";
            case REGISTER -> "Create your account";
            case APPOINTMENT -> "Book an appointment";
            case ECOMMERCE -> "Complete your order";
            case CATALOG -> "Browse products";
            case LANDING -> classPrefix;
            case HR -> "Employee request";
            case SUPPORT -> "Contact support";
            case FINANCE -> "Secure transfer";
            case DASHBOARD -> "Your workspace";
            case GENERIC -> classPrefix;
        };
    }

    private static String[] fieldLabelsFor(Domain domain) {
        return switch (domain) {
            case LOGIN -> new String[]{"Email or phone", "Password", "", ""};
            case REGISTER -> new String[]{"Full name", "Email", "Password", ""};
            case APPOINTMENT -> new String[]{"Patient / your name", "Preferred date", "Preferred time", "Reason for visit"};
            case ECOMMERCE -> new String[]{"Full name", "Shipping address", "Card / UPI", "Phone"};
            case CATALOG -> new String[]{"Search products", "Category", "", ""};
            case HR -> new String[]{"Employee name", "Request type", "From date", "Notes"};
            case SUPPORT -> new String[]{"Your name", "Email", "Subject", "Message"};
            case FINANCE -> new String[]{"From account", "To account / UPI", "Amount", "Remarks"};
            case LANDING -> new String[]{"Work email", "Company name", "Phone", "Message"};
            case DASHBOARD, GENERIC -> new String[]{"Your name", "Email", "Details", "Notes"};
        };
    }

    private static String extractFeatureName(String userStory, ScreenType type, Domain domain) {
        if (userStory != null) {
            Matcher m = Pattern.compile("(?i)\\b(?:a|an|the)\\s+([a-z][a-z0-9\\s-]{2,50}?)\\s+(?:page|screen|form|website|portal)\\b")
                    .matcher(userStory);
            if (m.find()) {
                return m.group(1).trim().replaceAll("\\s+", "-");
            }
            // Fallback: key noun phrases from examples
            Matcher m2 = Pattern.compile("(?i)\\b(appointment booking|product landing|login|registration|sign[- ]?up|checkout|contact|dashboard|pricing)\\b")
                    .matcher(userStory);
            if (m2.find()) {
                return m2.group(1).trim().replaceAll("\\s+", "-");
            }
        }
        return switch (domain) {
            case LOGIN -> "login";
            case REGISTER -> "register";
            case APPOINTMENT -> "appointment-booking";
            case ECOMMERCE -> "checkout";
            case CATALOG -> "product-catalog";
            case LANDING -> "company-landing";
            case HR -> "hr-request";
            case SUPPORT -> "support-contact";
            case FINANCE -> "money-transfer";
            case DASHBOARD -> "dashboard";
            case GENERIC -> "feature-screen";
        };
    }

    /**
     * Only treat clear navigation phrases as redirects.
     * Never equal to the main page path (that used to overwrite the real webpage with a dashboard).
     */
    public static String extractRedirectPath(String userStory, ScreenType type, String pagePath) {
        if (userStory != null && !userStory.isBlank()) {
            Matcher m = Pattern.compile(
                    "(?i)(?:redirect(?:ed|s)?\\s+to|navigate\\s+to|go\\s+to|access(?:es)?\\s+(?:my|the|a)|open\\s+(?:my|the|a))\\s+(?:the\\s+|my\\s+|a\\s+)?([a-z][a-z0-9\\s-]{1,40}?)(?:\\s+page|\\s+screen)?(?:\\s|$|,|\\.)")
                    .matcher(userStory);
            if (m.find()) {
                String target = m.group(1).trim().toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9\\s-]", "")
                        .replaceAll("\\s+", "-");
                if (!target.isBlank() && !target.equals("login") && !target.equals("sign-in")) {
                    String path = "/" + target;
                    if (!path.equalsIgnoreCase(pagePath)) {
                        return path;
                    }
                }
            }
        }
        return switch (type) {
            case LOGIN, REGISTER -> "/dashboard";
            default -> null;
        };
    }

    public static String toPascalCase(String raw) {
        String[] parts = raw.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() == 0 ? "Feature" : sb.toString();
    }
}
