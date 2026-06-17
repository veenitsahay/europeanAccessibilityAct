package com.vulmo.a11y.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scan")
public class Scan {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "start_url", nullable = false)
    private String startUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "max_pages", nullable = false)
    private int maxPages = 30;

    @Column(name = "org_name")
    private String orgName;

    @Column(name = "site_name")
    private String siteName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "engine_name")
    private String engineName;

    @Column(name = "engine_version")
    private String engineVersion;

    @Column(name = "pages_scanned")
    private Integer pagesScanned;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error")
    private String error;

    protected Scan() { }

    public Scan(String startUrl, int maxPages, String orgName, String siteName, String contactEmail) {
        this.startUrl = startUrl;
        this.maxPages = maxPages;
        this.orgName = orgName;
        this.siteName = siteName;
        this.contactEmail = contactEmail;
    }

    public UUID getId() { return id; }
    public String getStartUrl() { return startUrl; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getMaxPages() { return maxPages; }
    public String getOrgName() { return orgName; }
    public String getSiteName() { return siteName; }
    public String getContactEmail() { return contactEmail; }
    public String getEngineName() { return engineName; }
    public void setEngineName(String engineName) { this.engineName = engineName; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public Integer getPagesScanned() { return pagesScanned; }
    public void setPagesScanned(Integer pagesScanned) { this.pagesScanned = pagesScanned; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
