package com.vulmo.a11y.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "scan_page")
public class ScanPage {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id")
    private Scan scan;

    @Column(nullable = false)
    private String url;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "page_error")
    private String pageError;

    protected ScanPage() { }

    public ScanPage(Scan scan, String url, Integer httpStatus, String pageError) {
        this.scan = scan;
        this.url = url;
        this.httpStatus = httpStatus;
        this.pageError = pageError;
    }

    public UUID getId() { return id; }
    public Scan getScan() { return scan; }
    public String getUrl() { return url; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getPageError() { return pageError; }
}
