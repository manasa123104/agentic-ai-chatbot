package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        assertTrue(dash.contains("Download PNG"));
        assertTrue(dash.contains("Download SVG"));
        assertTrue(dash.contains("Download all charts as PNG"));
        assertTrue(files.containsKey("analytics/charts/01-volume.svg"));
        assertTrue(files.containsKey("analytics/charts/09-severity.svg"));
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

    @Test
    void qualitySnapshotLinksDefectSources() {
        Map<String, String> files = DataAnalystTemplates.files(new DataAnalystTemplates.Context(
                StageDomain.BANKING, "Java", "Fund transfer", 6, 4, 8, 3, 4,
                List.of("stories.md", "src/test/java/com/bank/transfer/FundTransferServiceTest.java",
                        "manual-tests/manual-test-cases.md")));
        String stats = files.get("analytics/STATS.md");
        String dash = files.get("analytics/dashboard.html");
        assertTrue(stats.contains("Where the defect estimates come from"), stats);
        assertTrue(stats.contains("stories.md"), stats);
        assertTrue(stats.contains("FundTransferServiceTest.java"), stats);
        assertTrue(stats.contains("user stories from the Business Analyst"), stats);
        assertTrue(dash.contains("id=\"defectSources\""), dash);
        assertTrue(dash.contains("href=\"../stories.md\""), dash);
        assertTrue(dash.contains("Tester unit test"), dash);
        assertTrue(dash.contains("Tester manual case"), dash);
    }
}
