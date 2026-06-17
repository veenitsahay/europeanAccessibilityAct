package com.vulmo.a11y.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Jackson DTOs mirroring the scanner CLI's stdout JSON. */
public final class ScannerResult {

    private ScannerResult() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Root(String startUrl, String scannedAt, Engine engine, List<PageResult> pages) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Engine(String name, String version) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageResult(String url, Integer status, List<ViolationResult> violations, String error) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ViolationResult(String ruleId, String impact, List<String> wcag,
                                  String helpUrl, List<NodeResult> nodes) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeResult(String selector, String html) { }
}
