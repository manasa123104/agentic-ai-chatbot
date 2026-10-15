package com.agentic.chatbot.stage;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainCatalogTest {

    @Test
    void hasAtLeast39GroupedDomainsWithSamples() {
        List<DomainCatalog.Domain> all = DomainCatalog.all();
        assertTrue(all.size() >= 39, "domains: " + all.size());
        assertEquals(all.size(), DomainCatalog.groups().values().stream().mapToInt(List::size).sum());
        assertEquals(DomainCatalog.labels(), StageDomain.SUGGESTIONS, "the dropdown and validation share one list");
        Set<String> ids = new HashSet<>();
        for (DomainCatalog.Domain d : all) {
            assertTrue(ids.add(d.id()), "duplicate " + d.id());
            assertEquals(3, d.samples().size(), d.label());
            if (!StageDomain.from(d.label()).builtIn()) {
                assertEquals(d.id(), StageDomain.from(d.label()).id(), "UI and server derive the same id");
            }
        }
        for (String expected : List.of("Banking", "Healthcare", "Insurance", "Retail", "Telecom", "Education / EdTech",
                "Government / Public Sector", "Energy / Utilities", "Aviation", "Gaming")) {
            assertTrue(DomainCatalog.labels().contains(expected), "missing " + expected);
        }
    }

    @Test
    void everyDomainHasAnEditableBusinessAnalystRequirement() {
        Set<String> seen = new HashSet<>();
        for (DomainCatalog.Domain d : DomainCatalog.all()) {
            String text = DomainCatalog.requirement(d);
            assertTrue(text.length() > 200 && text.contains("\n- "), d.label() + " has a real requirement");
            assertTrue(seen.add(text), d.label() + " has its own text");
        }
        String bank = DomainCatalog.requirementFor("Banking");
        assertTrue(bank.contains("OTP") && bank.contains("audit") && bank.toLowerCase().contains("limit"));
        assertTrue(DomainCatalog.requirementFor("Logistics / Supply Chain").contains("tracking number"));
        assertTrue(DomainCatalog.requirementFor("Healthcare").toLowerCase().contains("appointment"));
        String custom = DomainCatalog.requirementFor("Pet grooming");
        assertTrue(custom.contains("Pet grooming"), "generic template names the custom domain");
    }

    @Test
    void findsDomainsByLabelOrKeywordAndGivesRoles() {
        assertEquals("Healthcare", DomainCatalog.find("healthcare").orElseThrow().label());
        assertEquals("Insurance", DomainCatalog.find("motor insurance claims").orElseThrow().label());
        assertTrue(DomainCatalog.find("zzz").isEmpty());
        StageDomain health = StageDomain.from("Healthcare");
        assertEquals("healthcare", health.id());
        assertEquals("patient", health.customerRole().toLowerCase());
        assertTrue(StageDomain.from("Logistics / Supply Chain") == StageDomain.LOGISTICS);
    }
}
