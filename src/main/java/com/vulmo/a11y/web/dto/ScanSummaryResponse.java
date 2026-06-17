package com.vulmo.a11y.web.dto;

import com.vulmo.a11y.domain.Scan;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScanSummaryResponse(
        UUID id,
        String startUrl,
        String status,
        Integer pagesScanned,
        long totalViolations,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt,
        String error) {

    public static ScanSummaryResponse from(Scan scan, long totalViolations) {
        return new ScanSummaryResponse(
                scan.getId(),
                scan.getStartUrl(),
                scan.getStatus().name(),
                scan.getPagesScanned(),
                totalViolations,
                scan.getCreatedAt(),
                scan.getFinishedAt(),
                scan.getError());
    }
}
