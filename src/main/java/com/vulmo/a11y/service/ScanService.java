package com.vulmo.a11y.service;

import com.vulmo.a11y.domain.Scan;
import com.vulmo.a11y.domain.ScanPage;
import com.vulmo.a11y.domain.Violation;
import com.vulmo.a11y.repo.ScanPageRepository;
import com.vulmo.a11y.repo.ScanRepository;
import com.vulmo.a11y.repo.ViolationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ScanRepository scanRepository;
    private final ScanPageRepository pageRepository;
    private final ViolationRepository violationRepository;
    private final ScannerRunner scannerRunner;
    private final SsrfGuard ssrfGuard;
    private final int maxPagesCap;

    public ScanService(ScanRepository scanRepository,
                       ScanPageRepository pageRepository,
                       ViolationRepository violationRepository,
                       ScannerRunner scannerRunner,
                       SsrfGuard ssrfGuard,
                       @Value("${app.scanner.max-pages-cap}") int maxPagesCap) {
        this.scanRepository = scanRepository;
        this.pageRepository = pageRepository;
        this.violationRepository = violationRepository;
        this.scannerRunner = scannerRunner;
        this.ssrfGuard = ssrfGuard;
        this.maxPagesCap = maxPagesCap;
    }

    /** Validates, persists a PENDING scan, and returns it. Execution happens async. */
    public Scan create(String url, Integer maxPages, String orgName, String siteName, String contactEmail) {
        ssrfGuard.assertSafe(url);
        int pages = maxPages == null ? 30 : Math.min(Math.max(maxPages, 1), maxPagesCap);
        Scan scan = new Scan(url, pages, orgName, siteName, contactEmail);
        return scanRepository.save(scan);
    }

    @Async("scanExecutor")
    public void execute(UUID scanId) {
        Scan scan = scanRepository.findById(scanId).orElseThrow();
        scan.setStatus(Scan.Status.RUNNING);
        scan.setStartedAt(OffsetDateTime.now());
        scanRepository.save(scan);
        try {
            // Re-check just before launching: DNS can change between create and execute.
            ssrfGuard.assertSafe(scan.getStartUrl());
            ScannerResult.Root result = scannerRunner.run(scan.getStartUrl(), scan.getMaxPages());
            persistResult(scanId, result);
            log.info("Scan {} finished", scanId);
        } catch (Exception e) {
            log.error("Scan {} failed", scanId, e);
            Scan failed = scanRepository.findById(scanId).orElseThrow();
            failed.setStatus(Scan.Status.FAILED);
            failed.setError(abbreviate(e.getMessage()));
            failed.setFinishedAt(OffsetDateTime.now());
            scanRepository.save(failed);
        }
    }

    /**
     * Note: deliberately NOT @Transactional — this is self-invoked from execute(),
     * which bypasses Spring's proxy, so the annotation would silently do nothing.
     * Each save commits individually; a failed scan is marked FAILED in the catch.
     */
    protected void persistResult(UUID scanId, ScannerResult.Root result) {
        Scan scan = scanRepository.findById(scanId).orElseThrow();
        if (result.engine() != null) {
            scan.setEngineName(result.engine().name());
            scan.setEngineVersion(result.engine().version());
        }
        int pageCount = result.pages() == null ? 0 : result.pages().size();
        scan.setPagesScanned(pageCount);

        if (result.pages() != null) {
            for (ScannerResult.PageResult p : result.pages()) {
                ScanPage page = pageRepository.save(
                        new ScanPage(scan, p.url(), p.status(), abbreviate(p.error())));
                if (p.violations() == null) continue;
                for (ScannerResult.ViolationResult v : p.violations()) {
                    String wcag = v.wcag() == null ? null : String.join(",", v.wcag());
                    if (v.nodes() == null || v.nodes().isEmpty()) {
                        violationRepository.save(new Violation(
                                page, v.ruleId(), safeImpact(v.impact()), wcag, v.helpUrl(), null, null));
                    } else {
                        for (ScannerResult.NodeResult n : v.nodes()) {
                            violationRepository.save(new Violation(
                                    page, v.ruleId(), safeImpact(v.impact()), wcag,
                                    v.helpUrl(), n.selector(), n.html()));
                        }
                    }
                }
            }
        }
        scan.setStatus(Scan.Status.DONE);
        scan.setFinishedAt(OffsetDateTime.now());
        scanRepository.save(scan);
    }

    private static String safeImpact(String impact) {
        return impact == null ? "minor" : impact;
    }

    private static String abbreviate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
