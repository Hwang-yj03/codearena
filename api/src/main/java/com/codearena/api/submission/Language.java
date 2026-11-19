package com.codearena.api.submission;

import java.util.Arrays;

/** judge-worker의 languages.py 키와 1:1로 맞춘다. */
public enum Language {
    CPP("cpp"),
    PYTHON("python"),
    JAVA("java");

    private final String key;

    Language(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Language fromKey(String key) {
        return Arrays.stream(values())
                .filter(l -> l.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported language: " + key));
    }
}
