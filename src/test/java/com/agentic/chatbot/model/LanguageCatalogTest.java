package com.agentic.chatbot.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCatalogTest {

    private static String id(Optional<LanguageCatalog.Entry> e) {
        return e.map(LanguageCatalog.Entry::id).orElse("none");
    }

    private static String fileId(String name, String content) {
        return id(LanguageCatalog.forFile(name, () -> content));
    }

    @Test
    void listsTheSampleLanguagesFirstThenManyMore() {
        List<ProgrammingLanguage> samples = LanguageCatalog.sampleLanguages();
        List<ProgrammingLanguage> all = LanguageCatalog.allLanguages();
        assertEquals(15, samples.size());
        assertTrue(LanguageCatalog.moreLanguages().size() >= 40, "more languages: " + LanguageCatalog.moreLanguages().size());
        assertEquals(samples, all.subList(0, 15), "sample-library languages come first");
        Set<String> ids = new HashSet<>();
        for (LanguageCatalog.Entry e : LanguageCatalog.entries()) {
            assertTrue(ids.add(e.id()), "duplicate id " + e.id());
            assertFalse(e.name().isBlank(), e.id());
            assertTrue(e.extension().matches("[a-z0-9]+"), e.id() + " extension " + e.extension());
            assertFalse(e.fence().isBlank(), e.id());
        }
        for (String expected : List.of("scala", "dart", "r", "perl", "elixir", "haskell", "lua", "fsharp", "vbnet",
                "fortran", "solidity", "zig", "abap", "objective-c", "matlab", "bash", "powershell")) {
            assertTrue(ids.contains(expected), "missing " + expected);
        }
    }

    @Test
    void findsLanguagesByIdNameAliasOrMainExtension() {
        assertEquals("scala", id(LanguageCatalog.find("Scala")));
        assertEquals("csharp", id(LanguageCatalog.find("C#")));
        assertEquals("fsharp", id(LanguageCatalog.find("F#")));
        assertEquals("elixir", id(LanguageCatalog.find(".ex")));
        assertEquals("kotlin", id(LanguageCatalog.find("kt")));
        assertEquals("none", id(LanguageCatalog.find("for")), "extra extensions must not match chat words");
        assertEquals("none", id(LanguageCatalog.find("s")));
        assertEquals("none", id(LanguageCatalog.find("klingon")));
    }

    @Test
    void mapsFileExtensionsIncludingAmbiguousOnes() {
        assertEquals("scala", fileId("Main.scala", ""));
        assertEquals("haskell", fileId("lib/Parser.hs", ""));
        assertEquals("elixir", fileId("test/shop_test.exs", ""));
        assertEquals("typescript", fileId("App.tsx", ""));
        assertEquals("cpp", fileId("vector.hpp", ""));
        assertEquals("none", fileId("README.md", ""));
        assertEquals("none", fileId("Makefile", ""));

        assertEquals("matlab", fileId("solve.m", "function y = solve(x)\n  y = x * 2;\nend\n"));
        assertEquals("objective-c", fileId("AppDelegate.m", "#import <Foundation/Foundation.h>\n@interface AppDelegate : NSObject\n@end\n"));
        assertEquals("perl", fileId("tool.pl", "use strict;\nmy $x = 1;\nprint \"$x\\n\";\n"));
        assertEquals("prolog", fileId("family.pl", "parent(tom, bob).\ngrandparent(X, Z) :- parent(X, Y), parent(Y, Z).\n"));
        assertEquals("vb6", fileId("Account.cls", "VERSION 1.0 CLASS\nBEGIN\nEND\n"));
        assertEquals("apex", fileId("AccountService.cls", "public class AccountService { }"));

        List<String> extensions = LanguageCatalog.extensions();
        for (String ext : List.of(".scala", ".dart", ".ex", ".exs", ".hs", ".m", ".pl", ".jl", ".sol", ".vb")) {
            assertTrue(extensions.contains(ext), "missing " + ext);
        }
    }
}
