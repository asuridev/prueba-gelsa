package com.ecomerce.notifications.infrastructure.persistence.entities;

import com.ecomerce.notifications.domain.enums.TemplateVersionStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "email_template_versions", uniqueConstraints = @UniqueConstraint(name = "uk_email_template_versions_natural", columnNames = { "email_template_id", "version_number" }))
public class EmailTemplateVersionJpa extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

    @Column(name = "text_body", nullable = false, columnDefinition = "text")
    private String textBody;

    @ElementCollection
    @CollectionTable(name = "email_template_version_variables", joinColumns = @JoinColumn(name = "email_template_version_id", foreignKey = @ForeignKey(name = "fk_email_template_version_variables_email_template_version")), indexes = @Index(name = "ix_email_template_version_variables_email_template_version_id", columnList = "email_template_version_id"))
    @OrderColumn(name = "variables_order")
    @BatchSize(size = 100)
    private List<TemplateVariableJpa> variables = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TemplateVersionStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "email_template_id", nullable = false, foreignKey = @ForeignKey(name = "fk_email_template_versions_email_template"))
    private EmailTemplateJpa emailTemplate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public List<TemplateVariableJpa> getVariables() {
        return variables;
    }

    public void setVariables(List<TemplateVariableJpa> variables) {
        this.variables = variables;
    }

    public TemplateVersionStatus getStatus() {
        return status;
    }

    public void setStatus(TemplateVersionStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public EmailTemplateJpa getEmailTemplate() {
        return emailTemplate;
    }

    public void setEmailTemplate(EmailTemplateJpa emailTemplate) {
        this.emailTemplate = emailTemplate;
    }
}
