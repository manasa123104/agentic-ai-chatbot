package com.agentic.chatbot.stage;

import com.agentic.chatbot.model.LanguageCatalog;

import java.util.Optional;

/**
 * The code language of a stage request with the conventions the Developer, Tester and Deploy stages need.
 * {@code kind} is the built-in {@link StageLanguage} (with its offline templates); languages from the
 * {@link LanguageCatalog} beyond those, and free text, have kind {@link StageLanguage#OTHER} but keep their own
 * id, name, file extension and unit-test framework, so the AI model generates them like any other language.
 *
 * @param id            catalog id ("scala"), the built-in id ("go"), or "other" for free text
 * @param dockerImage   toolchain image for the generic deploy template (blank when unknown)
 */
public record LanguageProfile(StageLanguage kind, String id, String label, String ext, String fence,
                              String unitFramework, String srcDir, String testDir, String checkCommand,
                              String dockerImage) {

    private static final int MAX_NAME = 40;

    public static LanguageProfile of(StageLanguage language) {
        return new LanguageProfile(language, language.id(), language.label(), language.ext(), language.fence(),
                language.unitFramework(), language.srcDir(), language.testDir(), language.checkCommand(), "");
    }

    /**
     * Resolves the language option: a built-in language, a catalog language, or (for "other") the typed
     * {@code languageName}, itself resolved against the built-in and catalog languages first.
     */
    public static LanguageProfile resolve(String language, String languageName) {
        StageLanguage kind = StageLanguage.from(language);
        if (kind != StageLanguage.OTHER) {
            return of(kind);
        }
        boolean typed = language == null || language.isBlank() || language.trim().equalsIgnoreCase("other");
        String name = typed ? languageName : language;
        if (name != null && !name.isBlank()) {
            StageLanguage builtIn = StageLanguage.from(name);
            if (builtIn != StageLanguage.OTHER) {
                return of(builtIn);
            }
            Optional<LanguageCatalog.Entry> entry = LanguageCatalog.find(name);
            if (entry.isPresent()) {
                return entry.get().hasSamples() ? of(StageLanguage.from(entry.get().id())) : of(entry.get());
            }
        }
        String clean = name == null ? "" : name.replaceAll("[^\\w#+./ -]", "").strip();
        if (clean.isEmpty() || clean.equalsIgnoreCase("other")) {
            clean = "the requested language";
        }
        clean = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
        StageLanguage o = StageLanguage.OTHER;
        return new LanguageProfile(o, o.id(), clean, o.ext(), o.fence(), o.unitFramework(), o.srcDir(), o.testDir(),
                o.checkCommand(), "");
    }

    static LanguageProfile of(LanguageCatalog.Entry e) {
        return new LanguageProfile(StageLanguage.OTHER, e.id(), e.name(), e.extension(), e.fence(),
                e.unitFramework().isBlank() ? StageLanguage.OTHER.unitFramework() : e.unitFramework(),
                e.srcDir().isBlank() ? "src" : e.srcDir(), e.testDir().isBlank() ? "tests" : e.testDir(),
                e.checkCommand().isBlank() ? StageLanguage.OTHER.checkCommand() : e.checkCommand(), e.dockerImage());
    }

    /** True for catalog languages and built-ins; false for free text BOT does not know. */
    public boolean known() {
        return !id.equals(StageLanguage.OTHER.id());
    }
}
