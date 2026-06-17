package com.vulmo.a11y.report;

import com.vulmo.a11y.domain.Scan;
import com.vulmo.a11y.domain.Violation;
import com.vulmo.a11y.repo.ScanRepository;
import com.vulmo.a11y.repo.ViolationRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders the draft EAA accessibility statement.
 *
 * Two rules are enforced in code, deliberately:
 *  1. The statement NEVER claims "fully conformant" — automated testing cannot prove it.
 *  2. The methodology section (tool, version, date, page count, manual-audit caveat)
 *     is always present.
 */
@Service
public class StatementService {

    private final ScanRepository scanRepository;
    private final ViolationRepository violationRepository;
    private final MappingService mappingService;
    private final String template;

    public StatementService(ScanRepository scanRepository,
                            ViolationRepository violationRepository,
                            MappingService mappingService) {
        this.scanRepository = scanRepository;
        this.violationRepository = violationRepository;
        this.mappingService = mappingService;
        try (InputStream in = new ClassPathResource("templates/statement.md").getInputStream()) {
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load statement template", e);
        }
    }

    private record ScGroup(String wcagSc, String wcagName, String en301549, String plain,
                           Set<String> pages) { }

    @Transactional(readOnly = true)
    public String renderMarkdown(UUID scanId) {
        Scan scan = scanRepository.findById(scanId).orElseThrow();
        if (scan.getStatus() != Scan.Status.DONE) {
            throw new IllegalStateException("Scan is not finished; statement unavailable. Status: "
                    + scan.getStatus());
        }
        List<Violation> violations = violationRepository.findAllByScanId(scanId);

        // Group by WCAG success criterion via the mapping table.
        Map<String, ScGroup> bySc = new LinkedHashMap<>();
        for (Violation v : violations) {
            MappingService.RuleMapping m = mappingService.forRule(v.getRuleId(), v.getWcagSc());
            ScGroup g = bySc.computeIfAbsent(m.wcagSc(), sc -> new ScGroup(
                    m.wcagSc(), m.wcagName(), m.en301549(), m.plain(), new HashSet<>()));
            g.pages().add(v.getPage().getUrl());
        }

        String conformanceSection;
        String nonAccessibleContent;
        if (bySc.isEmpty()) {
            // Hard rule 1: never "fully conformant", even with zero findings.
            conformanceSection =
                    "No accessibility issues were detected by automated testing covering "
                    + pageCount(scan) + " page(s). Automated testing evaluates a subset of "
                    + "WCAG 2.1 success criteria; full conformance can only be confirmed "
                    + "through a manual audit, which has not yet been performed.";
            nonAccessibleContent =
                    "No non-conformities were identified by automated testing. "
                    + "This section should be revisited after a manual audit.";
        } else {
            conformanceSection =
                    "This website is **partially conformant** with WCAG 2.1 Level AA "
                    + "(EN 301 549). \"Partially conformant\" means that some parts of the "
                    + "content do not fully conform to the accessibility standard, as listed below.";
            StringBuilder sb = new StringBuilder();
            sb.append("The following non-conformities were identified by automated testing:\n\n");
            bySc.values().stream()
                .sorted(Comparator.comparing(ScGroup::wcagSc))
                .forEach(g -> sb.append("- **").append(g.wcagName())
                        .append("** (WCAG ").append(g.wcagSc())
                        .append("; EN 301 549 ").append(g.en301549()).append("): ")
                        .append(g.plain())
                        .append(" Affects approximately ").append(g.pages().size())
                        .append(" page(s). Remediation is planned.\n"));
            nonAccessibleContent = sb.toString();
        }

        String scanDate = scan.getFinishedAt() == null ? "unknown"
                : scan.getFinishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return template
                .replace("{{siteName}}", orDefault(scan.getSiteName(), scan.getStartUrl()))
                .replace("{{orgName}}", orDefault(scan.getOrgName(), "[Organisation name]"))
                .replace("{{startUrl}}", scan.getStartUrl())
                .replace("{{conformanceSection}}", conformanceSection)
                .replace("{{nonAccessibleContent}}", nonAccessibleContent)
                .replace("{{scanDate}}", scanDate)
                .replace("{{engineName}}", orDefault(scan.getEngineName(), "automated scanner"))
                .replace("{{engineVersion}}", orDefault(scan.getEngineVersion(), ""))
                .replace("{{pageCount}}", pageCount(scan))
                .replace("{{contactEmail}}", orDefault(scan.getContactEmail(), "[contact email]"))
                .replace("{{slaDays}}", "5");
    }

    private static String pageCount(Scan scan) {
        return scan.getPagesScanned() == null ? "0" : String.valueOf(scan.getPagesScanned());
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
