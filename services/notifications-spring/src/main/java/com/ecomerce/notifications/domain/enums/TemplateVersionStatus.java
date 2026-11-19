package com.ecomerce.notifications.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TemplateVersionStatus {

    ACTIVE("active"),
    ARCHIVED("archived");

    private final String value;

    TemplateVersionStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
