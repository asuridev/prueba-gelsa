package com.ecomerce.notifications.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class VariableValueJpa {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "`value`", nullable = false, length = 10000)
    private String value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
