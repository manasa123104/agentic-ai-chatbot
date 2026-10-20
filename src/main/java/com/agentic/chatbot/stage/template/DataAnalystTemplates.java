package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline Data Analyst artefacts: KPI stats, Chart.js dashboard and the JSON behind the charts.
 * Numbers are illustrative, domain-flavoured metrics derived from the run (stories / code / tests), not a live warehouse.
 * Each graph on the dashboard carries a "what this shows" note and an analysis of the series.
 */
public final class DataAnalystTemplates {

    private DataAnalystTemplates() {
    }

    /** What the analyst report needs about the current run. */
    public record Context(StageDomain domain, String languageName, String feature, int storyCount, int codeFiles,
                          int unitTests, int automationTests, int manualCases) {
    }

    public static Map<String, String> files(Context ctx) {
        Metrics m = metrics(ctx);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("analytics/metrics.json", m.json());
        out.put("analytics/STATS.md", statsMd(ctx, m));
        out.put("analytics/dashboard.html", dashboardHtml(ctx, m));
        return out;
    }

    private static Metrics metrics(Context ctx) {
        String d = ctx.domain().id().toLowerCase(Locale.ROOT);
        int stories = Math.max(ctx.storyCount(), 3);
        int code = Math.max(ctx.codeFiles(), 2);
        int unit = Math.max(ctx.unitTests(), 4);
        int auto = Math.max(ctx.automationTests(), 2);
        int manual = Math.max(ctx.manualCases(), 3);
        int coverage = Math.min(96, 55 + unit * 3 + auto * 2);
        int defects = Math.max(1, 12 - unit / 2);
        int open = Math.max(0, defects / 3);
        List<String> labels = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
        Pack pack;
        if (d.contains("bank") || d.contains("finance") || d.contains("payment")) {
            pack = new Pack(
                    List.of(120, 145, 132, 168, 190, 95, 80),
                    List.of(96, 97, 95, 98, 97, 94, 93),
                    "Transfer success rate", "AML flags / day", "Avg settlement (hrs)",
                    "transfers", "Weekly transfer volume",
                    "Each bar is how many fund transfers were submitted that weekday. BOT draws a typical retail-banking week from this run's stories — it is not connected to a live core-banking system.",
                    "Transfer success rate (%)",
                    "Share of transfers that completed without insufficient funds, frozen/closed accounts, daily-limit breaches or failed OTP. A dip is a signal to read Tester failures, not a live SLA feed.",
                    "Seven-day average of the success-rate line. Banking teams usually treat weekdays at or above 95% as healthy.",
                    "Estimated AML / screening flags per day, scaled from story count and leftover defects. Higher means more transfers need a compliance look.",
                    "Hours from debit to credit in the beneficiary ledger. Longer settlement often tracks open defects on the payment path.",
                    List.of("Mobile app", "Internet banking", "Branch", "Open API"),
                    "Where the week's transfers were submitted. Mobile and internet banking usually dominate; a large branch slice may mean the digital channel is hard to use.",
                    List.of("Insufficient funds", "Daily limit", "Frozen / closed", "OTP failed", "AML hold"),
                    "Why transfers did not complete. These counts are the unsuccessful remainder of weekly volume, split the way retail-payment exceptions typically fall — not a live fraud system.",
                    "Settlement time (hrs)", "hours",
                    List.of(2.1, 2.0, 2.3, 1.9, 2.2, 2.8, 3.1),
                    "Average hours from debit on the source account to credit on the beneficiary. Weekend bars often rise because clearing windows are thinner.");
        } else if (d.contains("health") || d.contains("hospital") || d.contains("clinic")) {
            pack = new Pack(
                    List.of(40, 55, 48, 62, 58, 30, 22),
                    List.of(92, 94, 93, 95, 94, 90, 91),
                    "Appointments kept", "No-shows / day", "Avg wait (min)",
                    "appointments", "Weekly appointment volume",
                    "Each bar is clinic appointments booked that weekday. Mid-week peaks are typical for follow-ups; weekend bars are usually urgent or walk-in only.",
                    "Appointments kept (%)",
                    "Share of booked slots where the patient attended. The remainder is no-shows plus late cancellations estimated from this run, not a live EHR extract.",
                    "Seven-day average of kept appointments. Clinics often aim for 93% or better on weekdays.",
                    "Estimated no-shows per day, scaled from leftover defects and story count.",
                    "Minutes from check-in to being seen. Longer waits often track open defects on scheduling or room-turnaround stories.",
                    List.of("Patient portal", "Phone", "Walk-in", "Referral"),
                    "How appointments were booked. Portal and phone should lead; a large walk-in slice can mean the booking flow is failing.",
                    List.of("No-show", "Late cancel", "Slot clash", "Room delay", "Insurance check"),
                    "Why booked slots were not kept or ran late. Split from the unsuccessful remainder of weekly appointments — not a live clinic system.",
                    "Wait time (min)", "minutes",
                    List.of(18.0, 16.0, 22.0, 14.0, 19.0, 28.0, 25.0),
                    "Average minutes from check-in to being seen. Weekend spikes often mean fewer rooms and more walk-ins.");
        } else if (d.contains("logistics") || d.contains("ship") || d.contains("fleet")) {
            pack = new Pack(
                    List.of(220, 210, 240, 255, 260, 180, 150),
                    List.of(91, 93, 92, 94, 95, 90, 89),
                    "On-time delivery", "Exceptions / day", "Avg transit (hrs)",
                    "shipments", "Weekly shipment volume",
                    "Each bar is outbound shipments handed to carriers that weekday. Friday is often the weekly peak before the weekend cut-off.",
                    "On-time delivery (%)",
                    "Share of shipments that arrived inside the promised window. Drops usually cluster with weather, capacity or address exceptions — here they are inferred from Tester results, not a live TMS.",
                    "Seven-day average of on-time deliveries. Networks often treat 93% as the floor before a recovery plan.",
                    "Estimated exception events per day (failed scan, wrong hub, damaged parcel), scaled from stories and open defects.",
                    "Average hours from pick-up to proof of delivery. Longer transit often tracks open defects on routing stories.",
                    List.of("Shipper portal", "EDI", "API", "Call centre"),
                    "How shipments were created. EDI and API should dominate for B2B; a large call-centre slice often means the portal is slow or unclear.",
                    List.of("Address exception", "Capacity", "Weather / delay", "Missed scan", "Damaged"),
                    "Why shipments missed the on-time window. Split from the unsuccessful remainder of weekly volume — not a live carrier feed.",
                    "Transit time (hrs)", "hours",
                    List.of(14.0, 13.2, 12.8, 12.1, 12.4, 18.0, 20.5),
                    "Average hours from pick-up to proof of delivery. Weekend rise is the usual cut-off and thinner line-haul.");
        } else {
            pack = new Pack(
                    List.of(80, 90, 85, 100, 110, 70, 60),
                    List.of(90, 91, 92, 93, 92, 88, 87),
                    "Requests completed", "Open exceptions", "Avg cycle (hrs)",
                    "requests", "Weekly request volume",
                    "Each bar is how many domain requests were submitted that weekday. BOT scales a typical week from this run's stories — not a live operations database.",
                    "Requests completed (%)",
                    "Share of requests that finished in the happy path. The rest are rejected, timed out or still open, estimated from Tester coverage in this run.",
                    "Seven-day average of completed requests. Use it as a health check before Deploy, not as a contractual SLA.",
                    "Estimated exceptions still open, scaled from leftover defects and story count.",
                    "Average hours from request to done. Longer cycles often track open defects on the main workflow.",
                    List.of("Web", "Mobile", "API", "Email / other"),
                    "Which channel raised the week's requests. Web and mobile should lead; a large email slice often means the self-service path is incomplete.",
                    List.of("Validation", "Timeout", "Auth failed", "Duplicate", "Unknown"),
                    "Why requests left the happy path. Split from the unsuccessful remainder of weekly volume — not a live ticketing system.",
                    "Cycle time (hrs)", "hours",
                    List.of(6.5, 6.0, 5.8, 5.5, 5.9, 7.2, 8.0),
                    "Average hours from request to done. Weekend rise usually means fewer staff and slower approvals.");
        }
        int aml = Math.max(2, stories + open);
        double settle = Math.round((2.4 + defects * 0.15) * 10.0) / 10.0;
        int peakIdx = indexOfMax(pack.volume());
        int lowIdx = indexOfMin(pack.success());
        int avgSuccess = avg(pack.success());
        String peakDay = labels.get(peakIdx);
        String lowDay = labels.get(lowIdx);
        List<Integer> completed = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        for (int i = 0; i < pack.volume().size(); i++) {
            int done = (int) Math.round(pack.volume().get(i) * pack.success().get(i) / 100.0);
            completed.add(done);
            failed.add(Math.max(0, pack.volume().get(i) - done));
        }
        int weekVol = sum(pack.volume());
        int weekFail = sum(failed);
        List<Integer> channelValues = split(weekVol, 45, 30, 15, 10);
        List<Integer> reasonValues = split(Math.max(weekFail, 5), 38, 24, 16, 12, 10);
        int topReasonIdx = indexOfMax(reasonValues);
        int topChannelIdx = indexOfMax(channelValues);
        int slowIdx = indexOfMaxDouble(pack.cycle());
        NamedSeries channels = new NamedSeries("Channel mix", pack.channelWhat(),
                "Largest channel is " + pack.channelLabels().get(topChannelIdx) + " ("
                        + channelValues.get(topChannelIdx) + " " + pack.volumeUnit()
                        + "). If a digital channel is thin, the stories for that path may be weak — add Tester coverage before Deploy.",
                pack.volumeUnit(), pack.channelLabels(), channelValues);
        NamedSeries reasons = new NamedSeries("Failure reasons", pack.reasonWhat(),
                "Top reason is " + pack.reasonLabels().get(topReasonIdx) + " ("
                        + reasonValues.get(topReasonIdx) + " of " + weekFail
                        + " unsuccessful items). Map that bar to a Given/When/Then scenario and a unit test; it is the cheapest defect to prevent.",
                "count", pack.reasonLabels(), reasonValues);
        List<Integer> severityValues = List.of(
                Math.max(1, open),
                Math.max(1, defects - open),
                Math.max(2, (unit + auto) / 3),
                Math.max(1, stories / 2));
        NamedSeries severity = new NamedSeries("Defect severity",
                "How estimated defects are graded: Critical (blocks the main path), Major (workaround exists), Minor (polish) and Info (docs / test gaps). Counts are inferred from open defects, test volume and stories — not a Jira export.",
                "Critical is " + severityValues.get(0) + " and Major is " + severityValues.get(1)
                        + ". Block Deploy while Critical is above zero on the happy path. Info items can wait until after release if coverage stays above 80%.",
                "count", List.of("Critical", "Major", "Minor", "Info"), severityValues);
        DoubleSeries cycle = new DoubleSeries(pack.cycleTitle(), pack.cycleWhat(),
                "Slowest day is " + labels.get(slowIdx) + " (" + pack.cycle().get(slowIdx) + " " + pack.cycleUnit()
                        + "). Read this next to volume: a slow day with high demand is a capacity problem; a slow day with low demand is usually a defect or a batch window.",
                pack.cycleUnit(), pack.cycle());
        String volumeAnalysis = "Peak is " + peakDay + " (" + pack.volume().get(peakIdx) + " " + pack.volumeUnit()
                + "). Weekend bars are lower because fewer people and systems submit work. If a weekday sits far below its neighbours, treat it as a channel or settlement risk and check Tester failures for that path.";
        String successAnalysis = "The line averages " + avgSuccess + "%. The weakest day is " + lowDay + " ("
                + pack.success().get(lowIdx)
                + "%). Read a dip together with volume and outcomes: high volume plus a lower success rate is the usual place defects hide.";
        String mixAnalysis = "This run has " + unit + " unit tests, " + auto + " automation tests and " + manual
                + " manual cases. Unit tests catch rule mistakes. Automation covers the screen path. Manual cases are for a person to walk Given/When/Then. A thin slice in any colour is a gap before Deploy.";
        String qualityAnalysis = "BOT estimates " + defects + " defects from test volume, with " + open
                + " still open. Coverage is " + coverage
                + "% (from unit + automation counts, capped at 96%). Triage open items on the critical path before Deploy; coverage under 80% is a release risk.";
        String outcomesWhat = "Each day's bar is split into completed (happy path) and unsuccessful "
                + pack.volumeUnit() + ". Height is the same as weekly volume; colour shows quality of that demand.";
        String outcomesAnalysis = "Unsuccessful " + pack.volumeUnit() + " total " + weekFail + " for the week (of "
                + weekVol + "). The tallest red segment is the day to investigate first — usually the same day as the success-rate dip.";
        String how = "Nine charts in two groups. Operations: volume, success rate, daily outcomes, channel mix and cycle time. "
                + "Quality: test mix, defect counts, failure reasons and severity. Numbers are illustrative analytics from this run's stories, "
                + code + " source files and tests — not a live warehouse. Read demand first, then quality of that demand, then how well tests proved the stories.";
        return new Metrics(labels, pack.volume(), pack.success(), List.of(unit, auto, manual),
                coverage, defects, open, stories, code, pack.kpiA(), pack.kpiB(), pack.kpiC(), aml, settle,
                pack.volumeUnit(), pack.volumeTitle(), pack.volumeWhat(), volumeAnalysis,
                pack.successTitle(), pack.successWhat(), successAnalysis,
                mixAnalysis, qualityAnalysis,
                pack.kpiAWhat(), pack.kpiBWhat(), pack.kpiCWhat(), how,
                channels, reasons, severity, cycle, completed, failed, outcomesWhat, outcomesAnalysis);
    }

    private static String statsMd(Context ctx, Metrics m) {
        return """
                # Data Analyst report — %s

                Generated by BOT's Data Analyst stage for feature **%s** (%s).

                > Figures below are **illustrative analytics** derived from this run's stories, code and tests.
                > They are not connected to a live database.

                ## How to read the dashboard

                %s

                ## Run snapshot

                | Metric | Value |
                | --- | ---: |
                | Domain | %s |
                | Language | %s |
                | User stories (approx.) | %d |
                | Source files (approx.) | %d |
                | Unit tests (approx.) | %d |
                | Automation tests (approx.) | %d |
                | Manual cases (approx.) | %d |
                | Estimated test coverage | %d%% |
                | Estimated open defects | %d |

                ## Domain KPIs

                | KPI | Value | What it means |
                | --- | ---: | --- |
                | %s | %d%% (7-day avg) | %s |
                | %s | %d | %s |
                | %s | %s | %s |

                ## What each graph shows

                Open [`dashboard.html`](dashboard.html) in a browser. Every chart has the same three parts: title, a short **High-level overview** note, then an **Analysis** of the numbers.

                ### 1. %s
                **High-level overview:** %s

                **Analysis:** %s

                ### 2. %s
                **High-level overview:** %s

                **Analysis:** %s

                ### 3. Daily outcomes
                **High-level overview:** %s

                **Analysis:** %s

                ### 4. %s
                **High-level overview:** %s

                **Analysis:** %s

                ### 5. %s
                **High-level overview:** %s

                **Analysis:** %s

                ### 6. Test mix
                **High-level overview:** Doughnut of how this run is tested — unit tests (one function or class), UI automation, and manual cases a person runs from Given/When/Then.

                **Analysis:** %s

                ### 7. Quality snapshot
                **High-level overview:** Estimated defects found in this run versus how many are still open. Coverage sits in the KPI strip, not on this bar chart, so the two bars stay in the same unit (count of defects).

                **Analysis:** %s

                ### 8. %s
                **High-level overview:** %s

                **Analysis:** %s

                ### 9. %s
                **High-level overview:** %s

                **Analysis:** %s

                Raw numbers are in [`metrics.json`](metrics.json).

                ## Recommendations

                1. Keep unit coverage above 80%% before release (%d%% estimated from this run).
                2. Triage the %d open defect estimate; block Deploy if critical paths fail.
                3. Re-run Tester after any Developer change, then refresh this report.
                """.formatted(
                ctx.domain().label(), ctx.feature(), ctx.languageName(),
                m.how(),
                ctx.domain().label(), ctx.languageName(),
                m.stories(), m.codeFiles(), m.testMix().get(0), m.testMix().get(1), m.testMix().get(2),
                m.coverage(), m.openDefects(),
                m.kpiA(), avg(m.success()), m.kpiAWhat(),
                m.kpiB(), m.amlOrExceptions(), m.kpiBWhat(),
                m.kpiC(), m.settleOrWait(), m.kpiCWhat(),
                m.volumeTitle(), m.volumeWhat(), m.volumeAnalysis(),
                m.successTitle(), m.successWhat(), m.successAnalysis(),
                m.outcomesWhat(), m.outcomesAnalysis(),
                m.channels().title(), m.channels().what(), m.channels().analysis(),
                m.cycle().title(), m.cycle().what(), m.cycle().analysis(),
                m.mixAnalysis(), m.qualityAnalysis(),
                m.reasons().title(), m.reasons().what(), m.reasons().analysis(),
                m.severity().title(), m.severity().what(), m.severity().analysis(),
                m.coverage(), m.openDefects());
    }

    private static int avg(List<Integer> values) {
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(90));
    }

    private static int sum(List<Integer> values) {
        int t = 0;
        for (int v : values) {
            t += v;
        }
        return t;
    }

    private static List<Integer> split(int total, int... percents) {
        List<Integer> out = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < percents.length; i++) {
            if (i == percents.length - 1) {
                out.add(Math.max(0, total - used));
            } else {
                int n = Math.max(0, (int) Math.round(total * percents[i] / 100.0));
                out.add(n);
                used += n;
            }
        }
        return out;
    }

    private static int indexOfMax(List<Integer> values) {
        int idx = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) > values.get(idx)) {
                idx = i;
            }
        }
        return idx;
    }

    private static int indexOfMaxDouble(List<Double> values) {
        int idx = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) > values.get(idx)) {
                idx = i;
            }
        }
        return idx;
    }

    private static int indexOfMin(List<Integer> values) {
        int idx = 0;
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) < values.get(idx)) {
                idx = i;
            }
        }
        return idx;
    }

    private static String dashboardHtml(Context ctx, Metrics m) {
        return DASHBOARD
                .replace("__DOC_TITLE__", escape(ctx.domain().label()))
                .replace("__DOMAIN__", escape(ctx.domain().label()))
                .replace("__FEATURE__", escape(ctx.feature()))
                .replace("__LANGUAGE__", escape(ctx.languageName()))
                .replace("__HOW__", escape(m.how()))
                .replace("__COVERAGE__", Integer.toString(m.coverage()))
                .replace("__STORIES__", Integer.toString(m.stories()))
                .replace("__OPEN__", Integer.toString(m.openDefects()))
                .replace("__AVG_OK__", Integer.toString(avg(m.success())))
                .replace("__KPI_A__", escape(m.kpiA()))
                .replace("__KPI_A_WHAT__", escape(m.kpiAWhat()))
                .replace("__KPI_B__", escape(m.kpiB()))
                .replace("__KPI_B_VAL__", Integer.toString(m.amlOrExceptions()))
                .replace("__KPI_B_WHAT__", escape(m.kpiBWhat()))
                .replace("__KPI_C__", escape(m.kpiC()))
                .replace("__KPI_C_VAL__", Double.toString(m.settleOrWait()))
                .replace("__KPI_C_WHAT__", escape(m.kpiCWhat()))
                .replace("__VOL_TITLE__", escape(m.volumeTitle()))
                .replace("__VOL_WHAT__", escape(m.volumeWhat()))
                .replace("__VOL_ANALYSIS__", escape(m.volumeAnalysis()))
                .replace("__VOL_UNIT__", escape(m.volumeUnit()))
                .replace("__OK_TITLE__", escape(m.successTitle()))
                .replace("__OK_WHAT__", escape(m.successWhat()))
                .replace("__OK_ANALYSIS__", escape(m.successAnalysis()))
                .replace("__OUT_WHAT__", escape(m.outcomesWhat()))
                .replace("__OUT_ANALYSIS__", escape(m.outcomesAnalysis()))
                .replace("__CH_TITLE__", escape(m.channels().title()))
                .replace("__CH_WHAT__", escape(m.channels().what()))
                .replace("__CH_ANALYSIS__", escape(m.channels().analysis()))
                .replace("__CY_TITLE__", escape(m.cycle().title()))
                .replace("__CY_WHAT__", escape(m.cycle().what()))
                .replace("__CY_ANALYSIS__", escape(m.cycle().analysis()))
                .replace("__CY_UNIT__", escape(m.cycle().unit()))
                .replace("__MIX_ANALYSIS__", escape(m.mixAnalysis()))
                .replace("__QUAL_ANALYSIS__", escape(m.qualityAnalysis()))
                .replace("__RSN_TITLE__", escape(m.reasons().title()))
                .replace("__RSN_WHAT__", escape(m.reasons().what()))
                .replace("__RSN_ANALYSIS__", escape(m.reasons().analysis()))
                .replace("__SEV_TITLE__", escape(m.severity().title()))
                .replace("__SEV_WHAT__", escape(m.severity().what()))
                .replace("__SEV_ANALYSIS__", escape(m.severity().analysis()))
                .replace("__DEFECTS__", Integer.toString(m.defects()))
                .replace("__LABELS__", jsonArrayStrings(m.labels()))
                .replace("__VOLUME__", jsonArrayInts(m.volume()))
                .replace("__SUCCESS__", jsonArrayInts(m.success()))
                .replace("__MIX__", jsonArrayInts(m.testMix()))
                .replace("__DONE__", jsonArrayInts(m.completed()))
                .replace("__FAIL__", jsonArrayInts(m.failed()))
                .replace("__CH_LABELS__", jsonArrayStrings(m.channels().labels()))
                .replace("__CH_VALUES__", jsonArrayInts(m.channels().values()))
                .replace("__RSN_LABELS__", jsonArrayStrings(m.reasons().labels()))
                .replace("__RSN_VALUES__", jsonArrayInts(m.reasons().values()))
                .replace("__SEV_LABELS__", jsonArrayStrings(m.severity().labels()))
                .replace("__SEV_VALUES__", jsonArrayInts(m.severity().values()))
                .replace("__CYCLE__", jsonArrayDoubles(m.cycle().values()));
    }

    private static final String DASHBOARD = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>Data Analyst — __DOC_TITLE__</title>
              <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
              <style>
                :root { --ink:#1c2430; --muted:#5c6570; --line:#d7dde5; --card:#fff; --bg:#f4f6f8; --accent:#2c5d68; --gold:#c4a35a; --good:#3d7a6a; --bad:#a12b2b; }
                * { box-sizing: border-box; }
                    body { margin: 0; font: 15px/1.55 "Times New Roman", Times, serif; color: var(--ink); background: var(--bg); }
                header { padding: 1.25rem 1.5rem; background: linear-gradient(120deg,#2c5d68,#3d7a6a); color: #fff; }
                header h1 { margin: 0 0 .25rem; font-size: 1.35rem; }
                header p { margin: 0; opacity: .92; font-size: .95rem; }
                main { max-width: 1140px; margin: 0 auto; padding: 1.25rem; display: grid; gap: 1.15rem; }
                .how { background: #e8f1f2; border: 1px solid #c5d7db; border-radius: 12px; padding: .9rem 1rem; }
                .how strong { color: var(--accent); }
                .how p { margin: .35rem 0 0; color: var(--ink); }
                .kpis { display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: .75rem; }
                .kpi { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: .9rem 1rem; }
                .kpi b { display: block; font-size: 1.4rem; color: var(--accent); }
                .kpi .lbl { color: var(--muted); font-size: .85rem; }
                .kpi .why { margin: .45rem 0 0; font-size: .8rem; color: var(--ink); line-height: 1.4; }
                h3.group { margin: .2rem 0 0; font-size: .95rem; letter-spacing: .04em; text-transform: uppercase; color: var(--accent); }
                .grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(320px,1fr)); gap: 1rem; }
                .card { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 1rem 1.05rem; display: grid; gap: .55rem; }
                .card.wide { grid-column: 1 / -1; }
                .card h2 { margin: 0; font-size: 1.05rem; }
                .what, .analysis { margin: 0; font-size: .88rem; line-height: 1.45; }
                .what { color: var(--muted); }
                .what em { font-style: normal; font-weight: 700; color: var(--accent); }
                .analysis { background: #f7f1e4; border-left: 3px solid var(--gold); padding: .55rem .7rem; border-radius: 0 8px 8px 0; }
                .analysis strong { color: #7a5a1a; }
                canvas { max-height: 280px; }
                .card.wide canvas { max-height: 300px; }
                footer { color: var(--muted); font-size: .8rem; padding: 0 1.5rem 1.5rem; max-width: 1140px; margin: 0 auto; }
              </style>
            </head>
            <body>
              <header>
                <h1>Data Analyst dashboard</h1>
                <p>__DOMAIN__ · __FEATURE__ · __LANGUAGE__</p>
              </header>
              <main>
                <section class="how">
                  <strong>How to read this dashboard</strong>
                  <p>__HOW__</p>
                </section>
                <section class="kpis">
                  <div class="kpi"><b>__COVERAGE__%</b><span class="lbl">Est. test coverage</span>
                    <p class="why">Share of code the Tester artefacts likely cover, estimated from unit and automation counts in this run (capped at 96%). Not a JaCoCo or coverage.xml file.</p></div>
                  <div class="kpi"><b>__STORIES__</b><span class="lbl">Stories in run</span>
                    <p class="why">User stories and scenarios the analyst counted from the Business Analyst output. This sizes demand for the charts below.</p></div>
                  <div class="kpi"><b>__OPEN__</b><span class="lbl">Open defects (est.)</span>
                    <p class="why">Rough leftover defects after tests. BOT infers this from test volume, not a bug tracker. Triage these before Deploy.</p></div>
                  <div class="kpi"><b>__AVG_OK__%</b><span class="lbl">__KPI_A__ (7-day avg)</span>
                    <p class="why">__KPI_A_WHAT__</p></div>
                  <div class="kpi"><b>__KPI_B_VAL__</b><span class="lbl">__KPI_B__</span>
                    <p class="why">__KPI_B_WHAT__</p></div>
                  <div class="kpi"><b>__KPI_C_VAL__</b><span class="lbl">__KPI_C__</span>
                    <p class="why">__KPI_C_WHAT__</p></div>
                </section>
                <h3 class="group">Operations</h3>
                <section class="grid">
                  <article class="card">
                    <h2>1. __VOL_TITLE__</h2>
                    <p class="what"><em>High-level overview.</em> __VOL_WHAT__</p>
                    <canvas id="vol" role="img" aria-label="__VOL_TITLE__"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __VOL_ANALYSIS__</p>
                  </article>
                  <article class="card">
                    <h2>2. __OK_TITLE__</h2>
                    <p class="what"><em>High-level overview.</em> __OK_WHAT__</p>
                    <canvas id="ok" role="img" aria-label="__OK_TITLE__"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __OK_ANALYSIS__</p>
                  </article>
                  <article class="card wide">
                    <h2>3. Daily outcomes</h2>
                    <p class="what"><em>High-level overview.</em> __OUT_WHAT__</p>
                    <canvas id="out" role="img" aria-label="Daily outcomes"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __OUT_ANALYSIS__</p>
                  </article>
                  <article class="card">
                    <h2>4. __CH_TITLE__</h2>
                    <p class="what"><em>High-level overview.</em> __CH_WHAT__</p>
                    <canvas id="ch" role="img" aria-label="__CH_TITLE__"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __CH_ANALYSIS__</p>
                  </article>
                  <article class="card">
                    <h2>5. __CY_TITLE__</h2>
                    <p class="what"><em>High-level overview.</em> __CY_WHAT__</p>
                    <canvas id="cycle" role="img" aria-label="__CY_TITLE__"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __CY_ANALYSIS__</p>
                  </article>
                </section>
                <h3 class="group">Quality and tests</h3>
                <section class="grid">
                  <article class="card">
                    <h2>6. Test mix</h2>
                    <p class="what"><em>High-level overview.</em> How this run is tested: unit tests (one function or class at a time), UI automation, and manual cases a person runs from the stories' Given / When / Then.</p>
                    <canvas id="mix" role="img" aria-label="Test mix"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __MIX_ANALYSIS__</p>
                  </article>
                  <article class="card">
                    <h2>7. Quality snapshot</h2>
                    <p class="what"><em>High-level overview.</em> Estimated defects found in this run versus how many are still open. Coverage is in the KPI strip so these two bars stay in the same unit (count of defects).</p>
                    <canvas id="qual" role="img" aria-label="Quality snapshot"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __QUAL_ANALYSIS__</p>
                  </article>
                  <article class="card wide">
                    <h2>8. __RSN_TITLE__</h2>
                    <p class="what"><em>High-level overview.</em> __RSN_WHAT__</p>
                    <canvas id="rsn" role="img" aria-label="__RSN_TITLE__"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __RSN_ANALYSIS__</p>
                  </article>
                  <article class="card">
                    <h2>9. __SEV_TITLE__</h2>
                    <p class="what"><em>High-level overview.</em> __SEV_WHAT__</p>
                    <canvas id="sev" role="img" aria-label="__SEV_TITLE__"></canvas>
                    <p class="analysis"><strong>Analysis.</strong> __SEV_ANALYSIS__</p>
                  </article>
                </section>
              </main>
              <footer>Illustrative charts from BOT Data Analyst · Chart.js · Open this file from the run folder or after a GitHub push. Not live production telemetry.</footer>
              <script>
                const labels = __LABELS__;
                const volume = __VOLUME__;
                const success = __SUCCESS__;
                const mix = __MIX__;
                const done = __DONE__;
                const fail = __FAIL__;
                const chLabels = __CH_LABELS__;
                const chValues = __CH_VALUES__;
                const rsnLabels = __RSN_LABELS__;
                const rsnValues = __RSN_VALUES__;
                const sevLabels = __SEV_LABELS__;
                const sevValues = __SEV_VALUES__;
                const cycle = __CYCLE__;
                const ink = '#2c5d68';
                const gold = '#c4a35a';
                const good = '#3d7a6a';
                const bad = '#a12b2b';
                const palette = [ink, good, gold, '#6b8cae', '#8b5e3c'];
                new Chart(document.getElementById('vol'), {
                  type: 'bar',
                  data: { labels, datasets: [{ label: '__VOL_UNIT__', data: volume, backgroundColor: ink }] },
                  options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, title: { display: true, text: '__VOL_UNIT__' } } } }
                });
                new Chart(document.getElementById('ok'), {
                  type: 'line',
                  data: { labels, datasets: [{ label: 'Success %', data: success, borderColor: ink, tension: .3, fill: false }] },
                  options: { scales: { y: { min: 80, max: 100, title: { display: true, text: 'percent' } } } }
                });
                new Chart(document.getElementById('out'), {
                  type: 'bar',
                  data: { labels, datasets: [
                    { label: 'Completed', data: done, backgroundColor: good, stack: 'day' },
                    { label: 'Unsuccessful', data: fail, backgroundColor: bad, stack: 'day' }
                  ] },
                  options: { scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true, title: { display: true, text: '__VOL_UNIT__' } } } }
                });
                new Chart(document.getElementById('ch'), {
                  type: 'doughnut',
                  data: { labels: chLabels, datasets: [{ data: chValues, backgroundColor: palette }] },
                  options: { plugins: { legend: { position: 'bottom' } } }
                });
                new Chart(document.getElementById('cycle'), {
                  type: 'line',
                  data: { labels, datasets: [{ label: '__CY_UNIT__', data: cycle, borderColor: gold, backgroundColor: 'rgba(196,163,90,.18)', fill: true, tension: .3 }] },
                  options: { scales: { y: { beginAtZero: true, title: { display: true, text: '__CY_UNIT__' } } } }
                });
                new Chart(document.getElementById('mix'), {
                  type: 'doughnut',
                  data: { labels: ['Unit', 'Automation', 'Manual'],
                    datasets: [{ data: mix, backgroundColor: [ink, good, gold] }] },
                  options: { plugins: { legend: { position: 'bottom' } } }
                });
                new Chart(document.getElementById('qual'), {
                  type: 'bar',
                  data: { labels: ['Estimated defects', 'Still open'],
                    datasets: [{ label: 'count', data: [__DEFECTS__, __OPEN__], backgroundColor: [ink, gold] }] },
                  options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
                });
                new Chart(document.getElementById('rsn'), {
                  type: 'bar',
                  data: { labels: rsnLabels, datasets: [{ label: 'count', data: rsnValues, backgroundColor: ink }] },
                  options: { indexAxis: 'y', plugins: { legend: { display: false } }, scales: { x: { beginAtZero: true, ticks: { precision: 0 } } } }
                });
                new Chart(document.getElementById('sev'), {
                  type: 'doughnut',
                  data: { labels: sevLabels, datasets: [{ data: sevValues, backgroundColor: [bad, '#c47a3a', gold, '#6b8cae'] }] },
                  options: { plugins: { legend: { position: 'bottom' } } }
                });
              </script>
            </body>
            </html>
            """;

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String jsonArrayStrings(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private static String jsonArrayInts(List<Integer> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.append(']').toString();
    }

    private static String jsonArrayDoubles(List<Double> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.append(']').toString();
    }

    private record Pack(List<Integer> volume, List<Integer> success,
                        String kpiA, String kpiB, String kpiC,
                        String volumeUnit, String volumeTitle, String volumeWhat,
                        String successTitle, String successWhat,
                        String kpiAWhat, String kpiBWhat, String kpiCWhat,
                        List<String> channelLabels, String channelWhat,
                        List<String> reasonLabels, String reasonWhat,
                        String cycleTitle, String cycleUnit, List<Double> cycle, String cycleWhat) {
    }

    private record NamedSeries(String title, String what, String analysis, String unit,
                               List<String> labels, List<Integer> values) {
    }

    private record DoubleSeries(String title, String what, String analysis, String unit, List<Double> values) {
    }

    private record Metrics(List<String> labels, List<Integer> volume, List<Integer> success, List<Integer> testMix,
                           int coverage, int defects, int openDefects, int stories, int codeFiles,
                           String kpiA, String kpiB, String kpiC, int amlOrExceptions, double settleOrWait,
                           String volumeUnit, String volumeTitle, String volumeWhat, String volumeAnalysis,
                           String successTitle, String successWhat, String successAnalysis,
                           String mixAnalysis, String qualityAnalysis,
                           String kpiAWhat, String kpiBWhat, String kpiCWhat, String how,
                           NamedSeries channels, NamedSeries reasons, NamedSeries severity, DoubleSeries cycle,
                           List<Integer> completed, List<Integer> failed,
                           String outcomesWhat, String outcomesAnalysis) {
        String json() {
            return """
                    {
                      "disclaimer": "Illustrative analytics from this BOT run, not a live database.",
                      "howToRead": %s,
                      "labels": %s,
                      "volume": %s,
                      "successRate": %s,
                      "completed": %s,
                      "unsuccessful": %s,
                      "cycle": %s,
                      "channels": { "labels": %s, "values": %s },
                      "failureReasons": { "labels": %s, "values": %s },
                      "severity": { "labels": %s, "values": %s },
                      "testMix": { "unit": %d, "automation": %d, "manual": %d },
                      "coveragePercent": %d,
                      "defectsEstimated": %d,
                      "openDefectsEstimated": %d,
                      "stories": %d,
                      "codeFiles": %d,
                      "kpis": {
                        "%s": %d,
                        "%s": %d,
                        "%s": %s
                      },
                      "charts": [
                        { "id": "volume", "title": %s, "what": %s, "analysis": %s },
                        { "id": "success", "title": %s, "what": %s, "analysis": %s },
                        { "id": "outcomes", "title": "Daily outcomes", "what": %s, "analysis": %s },
                        { "id": "channels", "title": %s, "what": %s, "analysis": %s },
                        { "id": "cycle", "title": %s, "what": %s, "analysis": %s },
                        { "id": "testMix", "title": "Test mix", "what": "Unit vs automation vs manual counts from the Tester stage.", "analysis": %s },
                        { "id": "quality", "title": "Quality snapshot", "what": "Estimated defects versus still-open defects.", "analysis": %s },
                        { "id": "reasons", "title": %s, "what": %s, "analysis": %s },
                        { "id": "severity", "title": %s, "what": %s, "analysis": %s }
                      ]
                    }
                    """.formatted(
                    jsonString(how),
                    jsonArrayStrings(labels), jsonArrayInts(volume), jsonArrayInts(success),
                    jsonArrayInts(completed), jsonArrayInts(failed), jsonArrayDoubles(cycle.values()),
                    jsonArrayStrings(channels.labels()), jsonArrayInts(channels.values()),
                    jsonArrayStrings(reasons.labels()), jsonArrayInts(reasons.values()),
                    jsonArrayStrings(severity.labels()), jsonArrayInts(severity.values()),
                    testMix.get(0), testMix.get(1), testMix.get(2),
                    coverage, defects, openDefects, stories, codeFiles,
                    kpiA.replace("\"", ""), avg(success),
                    kpiB.replace("\"", ""), amlOrExceptions,
                    kpiC.replace("\"", ""), settleOrWait,
                    jsonString(volumeTitle), jsonString(volumeWhat), jsonString(volumeAnalysis),
                    jsonString(successTitle), jsonString(successWhat), jsonString(successAnalysis),
                    jsonString(outcomesWhat), jsonString(outcomesAnalysis),
                    jsonString(channels.title()), jsonString(channels.what()), jsonString(channels.analysis()),
                    jsonString(cycle.title()), jsonString(cycle.what()), jsonString(cycle.analysis()),
                    jsonString(mixAnalysis), jsonString(qualityAnalysis),
                    jsonString(reasons.title()), jsonString(reasons.what()), jsonString(reasons.analysis()),
                    jsonString(severity.title()), jsonString(severity.what()), jsonString(severity.analysis()));
        }
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
