package com.edtronaut.worker.service;

import java.util.List;

import com.edtronaut.worker.model.Language;

public record LanguageConfig(
        String fileName,
        String dockerImage,
        List<String> compileCommand,
        List<String> runCommand) {

    public static LanguageConfig forLanguage(Language language) {
        return switch (language) {
            case PYTHON -> new LanguageConfig(
                    "main.py",
                    "python:3.12-slim",
                    null,
                    List.of("python", "main.py"));

            case NODEJS -> new LanguageConfig(
                    "index.js",
                    "node:18-slim",
                    null,
                    List.of("node", "index.js"));

            case JAVA -> new LanguageConfig(
                    "Main.java",
                    "eclipse-temurin:22",
                    List.of("javac", "Main.java"),
                    List.of("java", "-Xmx128m", "Main"));
        };
    }
}
