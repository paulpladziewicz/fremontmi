package com.paulpladziewicz.fremontmi.models;

public enum ContentVisibility {
    PUBLIC("public"),
    RESTRICTED("restricted"),
    PRIVATE("private");

    private final String visibility;

    ContentVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getVisibility() {
        return visibility;
    }
}
