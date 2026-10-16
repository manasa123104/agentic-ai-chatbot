package com.agentic.chatbot.stage;

/**
 * A stage run folder that is not on this server (any more). Hosting platforms such as Render keep generated files on an
 * ephemeral disk, so after a restart or redeploy a run id that a browser still remembers no longer exists.
 */
public class RunNotFoundException extends IllegalArgumentException {

    /** Machine-readable code the web page looks for in the JSON error body. */
    public static final String CODE = "run_not_found";

    public RunNotFoundException(String runId) {
        super("Run " + runId + " not found. This run is no longer on the server (hosting restarted). "
                + "Press New run, then Run all stages, then push again.");
    }
}
