package com.ecomerce.notifications.infrastructure.persistence.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "email_templates", uniqueConstraints = @UniqueConstraint(name = "uk_email_templates_natural", columnNames = { "code" }))
public class EmailTemplateJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, columnDefinition = "varchar(255) collate \"C\"")
    private String code;

    @Column(name = "name", nullable = false, columnDefinition = "varchar(120) collate \"C\"")
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "has_active_version")
    private Boolean hasActiveVersion;

    @OneToMany(mappedBy = "emailTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<EmailTemplateVersionJpa> versions = new ArrayList<>();

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getHasActiveVersion() {
        return hasActiveVersion;
    }

    public void setHasActiveVersion(Boolean hasActiveVersion) {
        this.hasActiveVersion = hasActiveVersion;
    }

    public List<EmailTemplateVersionJpa> getVersions() {
        return versions;
    }

    public void setVersions(List<EmailTemplateVersionJpa> versions) {
        this.versions = versions;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }
}
