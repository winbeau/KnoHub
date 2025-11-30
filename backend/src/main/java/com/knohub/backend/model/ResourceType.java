package com.knohub.backend.model;

/**
 * Resource type enum matching frontend types
 */
public enum ResourceType {
    COURSE("course"),
    TECH("tech"),
    INFO("info");

    private final String value;

    ResourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
