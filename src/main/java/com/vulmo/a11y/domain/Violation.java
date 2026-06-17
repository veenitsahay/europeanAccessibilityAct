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
@Table(name = "violation")
public class Violation {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id")
    private ScanPage page;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(nullable = false)
    private String impact;

    /** Comma-separated WCAG success criteria, e.g. "1.4.3" or "1.1.1,4.1.2". */
    @Column(name = "wcag_sc")
    private String wcagSc;

    @Column(name = "help_url")
    private String helpUrl;

    @Column
    private String selector;

    @Column
    private String html;

    protected Violation() { }

    public Violation(ScanPage page, String ruleId, String impact, String wcagSc,
                     String helpUrl, String selector, String html) {
        this.page = page;
        this.ruleId = ruleId;
        this.impact = impact;
        this.wcagSc = wcagSc;
        this.helpUrl = helpUrl;
        this.selector = selector;
        this.html = html;
    }

    public UUID getId() { return id; }
    public ScanPage getPage() { return page; }
    public String getRuleId() { return ruleId; }
    public String getImpact() { return impact; }
    public String getWcagSc() { return wcagSc; }
    public String getHelpUrl() { return helpUrl; }
    public String getSelector() { return selector; }
    public String getHtml() { return html; }
}
