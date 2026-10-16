package com.agentic.chatbot.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The Stop button's server side. A stop is a flag per run: long jobs look at it between their steps (the bots of the
 * chat pipeline, the files of a conversion) and finish cleanly with what they have, so progress is kept and the run can
 * be resumed from the next step. Also hands out one lock per run, so a Resume that arrives while the previous request is
 * still finishing its current step waits for it instead of running the same step twice.
 */
@Component
public class RunControl {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{6,64}");

    private final Set<String> stops = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /** True for ids a browser may use as a run id (letters, digits, _ and -). */
    public static boolean validId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }

    public void requestStop(String runId) {
        if (runId != null) {
            stops.add(runId);
        }
    }

    public boolean stopRequested(String runId) {
        return runId != null && stops.contains(runId);
    }

    public void clear(String runId) {
        if (runId != null) {
            stops.remove(runId);
        }
    }

    /** The monitor to hold while a run executes; the same object for the same id. */
    public Object lock(String runId) {
        return locks.computeIfAbsent(runId, k -> new Object());
    }

    /** Frees the per-run bookkeeping once a run is finished for good. */
    public void forget(String runId) {
        stops.remove(runId);
        locks.remove(runId);
    }
}
