package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAnalystTemplatesTest {

    @Test
    void bankingDashboardExplainsEachGraph() {
        Map<String, String> files = DataAnalystTemplates.files(new DataAnalystTemplates.Context(
                StageDomain.BANKING, "Java", "Fund transfer", 6, 4, 8, 3, 4));
        String dash = files.get("analytics/dashboard.html");
        assertTrue(dash.contains("High-level overview"));
        assertTrue(dash.contains("Weekly transfer volume"));
        assertTrue(dash.contains("Transfer success rate"));
        assertTrue(dash.contains("Test mix"));
        assertTrue(dash.contains("Quality snapshot"));
        assertTrue(dash.contains("Daily outcomes"));
        assertTrue(dash.contains("Channel mix"));
        assertTrue(dash.contains("Failure reasons"));
        assertTrue(dash.contains("Defect severity"));
        assertTrue(dash.contains("Settlement time"));
        assertTrue(dash.contains("<strong>Analysis.</strong>"));
        assertTrue(dash.contains("How to read this dashboard"));
        assertTrue(dash.contains("id=\"qual\""));
        assertTrue(dash.contains("id=\"out\""));
        assertTrue(dash.contains("id=\"rsn\""));
        assertTrue(dash.contains("id=\"sev\""));
        assertTrue(dash.contains("id=\"cycle\""));
        assertTrue(dash.contains("id=\"ch\""));
        String stats = files.get("analytics/STATS.md");
        assertTrue(stats.contains("What each graph shows"));
        assertTrue(stats.contains("**Analysis:**"));
        String json = files.get("analytics/metrics.json");
        assertTrue(json.contains("\"id\": \"volume\""));
        assertTrue(json.contains("\"analysis\""));
    }

    @Test
    void healthcareUsesAppointmentCopy() {
        String dash = DataAnalystTemplates.files(new DataAnalystTemplates.Context(
                StageDomain.from("Healthcare"), "Python", "Patient appointment", 4, 3, 5, 2, 3))
                .get("analytics/dashboard.html");
        assertTrue(dash.contains("Weekly appointment volume"), dash);
        assertTrue(dash.contains("Appointments kept"), dash);
        assertTrue(dash.contains("Patient portal"), dash);
        assertTrue(dash.contains("No-show"), dash);
        assertTrue(dash.contains("Wait time"), dash);
    }
}
