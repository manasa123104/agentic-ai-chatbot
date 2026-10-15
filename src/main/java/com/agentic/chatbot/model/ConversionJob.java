package com.agentic.chatbot.model;

import java.util.ArrayList;
import java.util.List;

/** A project / repository conversion running in the background. Saved as job.json next to its files. */
public class ConversionJob {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    /**
     * @param status   converted, copied (same language or non-code file), skipped, or failed
     * @param mode     sample-library, llm, copied or none
     * @param parts    number of model calls for a large file
     */
    public record FileResult(String sourcePath, String targetPath, String sourceLanguage, String status, String mode,
                             int parts, String notes, String error, String businessLogic, long durationMs) {
        /** Backward-compatible constructor without timing. */
        public FileResult(String sourcePath, String targetPath, String sourceLanguage, String status, String mode,
                          int parts, String notes, String error, String businessLogic) {
            this(sourcePath, targetPath, sourceLanguage, status, mode, parts, notes, error, businessLogic, 0L);
        }

        public FileResult withDuration(long ms) {
            return new FileResult(sourcePath, targetPath, sourceLanguage, status, mode, parts, notes, error,
                    businessLogic, ms);
        }
    }

    /** A push of the converted output to a Git branch. */
    public record PushRecord(String repoUrl, String branch, String baseBranch, String folder, String branchUrl,
                             String compareUrl, String commit, String pushedAt) {
    }

    private String id;
    private Status status = Status.QUEUED;
    private String message = "Waiting to start";
    private String sourceLabel;
    private String sourceFolder = "";
    private String sourceLanguage;
    private String targetLanguageId;
    private String targetLanguage;
    private String instructions;
    private boolean addComments;
    private boolean explainLogic;
    private int totalFiles;
    private int doneFiles;
    private String currentFile;
    private String createdAt;
    private String startedAt;
    private String finishedAt;
    /** Total run time in milliseconds (set when the job finishes; live value is computed from startedAt). */
    private long durationMs;
    private String model;
    private String projectSummary;
    private List<FileResult> files = new ArrayList<>();
    private List<String> generatedFiles = new ArrayList<>();
    private List<String> skipped = new ArrayList<>();
    private List<PushRecord> pushes = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
    /** Sub-folder of the uploaded / cloned project that was converted ("" for the whole project). */
    public String getSourceFolder() { return sourceFolder; }
    public void setSourceFolder(String sourceFolder) { this.sourceFolder = sourceFolder; }
    public String getSourceLanguage() { return sourceLanguage; }
    public void setSourceLanguage(String sourceLanguage) { this.sourceLanguage = sourceLanguage; }
    public String getTargetLanguageId() { return targetLanguageId; }
    public void setTargetLanguageId(String targetLanguageId) { this.targetLanguageId = targetLanguageId; }
    public String getTargetLanguage() { return targetLanguage; }
    public void setTargetLanguage(String targetLanguage) { this.targetLanguage = targetLanguage; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isAddComments() { return addComments; }
    public void setAddComments(boolean addComments) { this.addComments = addComments; }
    public boolean isExplainLogic() { return explainLogic; }
    public void setExplainLogic(boolean explainLogic) { this.explainLogic = explainLogic; }
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    public int getDoneFiles() { return doneFiles; }
    public void setDoneFiles(int doneFiles) { this.doneFiles = doneFiles; }
    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getProjectSummary() { return projectSummary; }
    public void setProjectSummary(String projectSummary) { this.projectSummary = projectSummary; }
    public List<FileResult> getFiles() { return files; }
    public void setFiles(List<FileResult> files) { this.files = files; }
    public List<String> getGeneratedFiles() { return generatedFiles; }
    public void setGeneratedFiles(List<String> generatedFiles) { this.generatedFiles = generatedFiles; }
    public List<String> getSkipped() { return skipped; }
    public void setSkipped(List<String> skipped) { this.skipped = skipped; }
    public List<PushRecord> getPushes() { return pushes; }
    public void setPushes(List<PushRecord> pushes) { this.pushes = pushes; }
}
