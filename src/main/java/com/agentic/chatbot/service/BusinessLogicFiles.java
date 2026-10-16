package com.agentic.chatbot.service;

import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.model.ConversionJob.FileResult;

import java.util.Set;

/**
 * The plain-English business-logic companions of converted code: {@code Foo.py} gets {@code Foo.business-logic.md}, and a
 * project-level {@code BUSINESS_LOGIC.md} (or a short note saying it was not requested) goes into every ZIP.
 */
public final class BusinessLogicFiles {

    public static final String PROJECT_FILE = "BUSINESS_LOGIC.md";
    static final String NOT_REQUESTED = "Business logic was not requested for this conversion. Tick \"Explain the business "
            + "logic in plain English\" before converting to get a description of every file.";

    private BusinessLogicFiles() {
    }

    /** {@code dir/Foo.py} becomes {@code dir/Foo.business-logic.md}; {@code dir/Foo.py.business-logic.md} when that name is taken. */
    public static String companionName(String path, Set<String> taken) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        String base = dot > slash + 1 ? path.substring(0, dot) : path;
        String name = base + ".business-logic.md";
        return taken != null && taken.contains(name) ? path + ".business-logic.md" : name;
    }

    /** The file name only, for Content-Disposition. */
    public static String downloadName(String path) {
        String name = companionName(path, null);
        return name.substring(name.lastIndexOf('/') + 1);
    }

    /** Markdown for one converted file; a short note when no description was generated for it. */
    public static String fileMarkdown(ConversionJob job, FileResult file, String path) {
        StringBuilder sb = new StringBuilder("# Business logic: ").append(path).append("\n\n");
        if (file != null) {
            sb.append("Converted from `").append(file.sourcePath()).append("` (").append(file.sourceLanguage())
                    .append(") to ").append(job.getTargetLanguage()).append(".\n\n");
        }
        if (file != null && file.businessLogic() != null && !file.businessLogic().isBlank()) {
            sb.append(file.businessLogic().strip()).append('\n');
        } else {
            sb.append(job.isExplainLogic()
                    ? "No plain-English description was produced for this file.\n"
                    : NOT_REQUESTED + "\n");
        }
        if (job.getProjectSummary() != null && !job.getProjectSummary().isBlank() && job.getFiles().size() > 1) {
            sb.append("\n## Whole project\n\n").append(job.getProjectSummary().strip()).append('\n');
        }
        return sb.toString();
    }

    /** Stand-in for BUSINESS_LOGIC.md when the job did not explain the business logic. */
    public static String projectNote(ConversionJob job) {
        return "# Business logic\n\nProject: " + job.getSourceLabel() + "\n\n" + NOT_REQUESTED + "\n";
    }
}
