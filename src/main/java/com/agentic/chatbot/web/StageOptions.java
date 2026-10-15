package com.agentic.chatbot.web;

import com.agentic.chatbot.model.LanguageCatalog;
import com.agentic.chatbot.stage.DomainCatalog;
import com.agentic.chatbot.stage.StageLanguage;
import com.agentic.chatbot.stage.template.PolyglotTemplates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Domain and language dropdown data for the stages page and GET /api/stages/domains and /languages. */
final class StageOptions {

    private StageOptions() {
    }

    /** Domain groups in display order: [{group, domains: [{id, label, samples}]}]. */
    static List<Map<String, Object>> domainGroups() {
        List<Map<String, Object>> out = new ArrayList<>();
        DomainCatalog.groups().forEach((group, domains) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("group", group);
            g.put("domains", domains.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.id());
                m.put("label", d.label());
                m.put("samples", d.samples());
                m.put("requirement", DomainCatalog.requirement(d));
                return m;
            }).toList());
            out.add(g);
        });
        return out;
    }

    /**
     * Languages in dropdown order: the 15 built-in languages (offline samples and templates), then the catalog's
     * other languages (AI model). "Other (type your own)" is added by the page.
     */
    static List<Map<String, String>> languages() {
        List<Map<String, String>> out = new ArrayList<>();
        for (StageLanguage l : StageLanguage.values()) {
            if (l != StageLanguage.OTHER) {
                boolean offline = l == StageLanguage.JAVA || l == StageLanguage.PYTHON || PolyglotTemplates.supports(l);
                out.add(language(l.id(), l.label(), l.ext(), l.unitFramework(), l.defaultAutomation(), "samples", offline));
            }
        }
        String automation = StageLanguage.OTHER.defaultAutomation();
        for (LanguageCatalog.Entry e : LanguageCatalog.entries()) {
            if (!e.hasSamples()) {
                String unit = e.unitFramework().isBlank() ? StageLanguage.OTHER.unitFramework() : e.unitFramework();
                out.add(language(e.id(), e.name(), e.extension(), unit, automation, "more", false));
            }
        }
        return out;
    }

    private static Map<String, String> language(String id, String label, String ext, String unit, String automation,
                                                String group, boolean offline) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("ext", ext);
        m.put("unit", unit);
        m.put("automation", automation);
        m.put("group", group);
        m.put("offline", String.valueOf(offline));
        return m;
    }
}
