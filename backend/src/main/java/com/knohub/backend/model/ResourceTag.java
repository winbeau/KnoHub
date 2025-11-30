package com.knohub.backend.model;

/**
 * Resource tag enum for highlighting resources
 */
public enum ResourceTag {
    NEW("New"),
    HOT("Hot"),
    REC("Rec");

    private final String value;

    ResourceTag(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
