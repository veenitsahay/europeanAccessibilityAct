package com.vulmo.a11y.report;

import com.vulmo.a11y.domain.Scan;
import com.vulmo.a11y.domain.Violation;
import com.vulmo.a11y.repo.ScanRepository;
import com.vulmo.a11y.repo.ViolationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns raw violations into a prioritized developer task list.
 * One task per axe rule; score = impact weight x pages affected.
 */
@Service
public class TaskListService {

    private static final Map<String, Integer> IMPACT_WEIGHT = Map.of(
            "critical", 4, "serious", 3, "moderate", 2, "minor", 1);
    private static final int MAX_EXAMPLES_PER_TASK = 5;

    private final ScanRepository scanRepository;
    private final ViolationRepository violationRepository;
    private final MappingService mappingService;

    public TaskListService(ScanRepository scanRepository,
                           ViolationRepository violationRepository,
                           MappingService mappingService) {
        this.scanRepository = scanRepository;
        this.violationRepository = violationRepository;
        this.mappingService = mappingService;
    }

    private record Group(String ruleId, String impact, String wcagSc, String helpUrl,
                         Set<String> pages, List<Violation> examples) { }

    @Transactional(readOnly = true)
    public String renderMarkdown(UUID scanId) {
        Scan scan = scanRepository.findById(scanId).orElseThrow();
        List<Violation> violations = violationRepository.findAllByScanId(scanId);

        Map<String, Group> groups = new LinkedHashMap<>();
        for (Violation v : violations) {
            Group g = groups.computeIfAbsent(v.getRuleId(), id -> new Group(
                    id, v.getImpact(), v.getWcagSc(), v.getHelpUrl(),
                    new HashSet<>(), new ArrayList<>()));
            g.pages().add(v.getPage().getUrl());
            if (g.examples().size() < MAX_EXAMPLES_PER_TASK) {
                g.examples().add(v);
            }
        }

        List<Group> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparingInt(this::score).reversed());

        StringBuilder md = new StringBuilder();
        md.append("# Accessibility Task List — ").append(scan.getStartUrl()).append("\n\n");
        md.append("Scan date: ").append(scan.getFinishedAt()).append(" · Pages scanned: ")
          .append(scan.getPagesScanned()).append(" · Standard: WCAG 2.1 AA / EN 301 549\n\n");

        if (ordered.isEmpty()) {
            md.append("No issues were detected by automated testing. Note that automated tools ")
              .append("cover only a subset of WCAG; manual screen reader and keyboard testing ")
              .append("is required to verify conformance.\n");
            return md.toString();
        }

        md.append("**").append(ordered.size()).append(" issue type(s)** found across ")
          .append(countDistinctPages(violations)).append(" page(s), ordered by priority ")
          .append("(severity × pages affected). Fix from the top down.\n\n");

        int rank = 1;
        for (Group g : ordered) {
            MappingService.RuleMapping m = mappingService.forRule(g.ruleId(), g.wcagSc());
            md.append("## ").append(rank++).append(". ").append(taskTitle(g.ruleId()))
              .append("    [").append(g.impact()).append(" — ")
              .append(g.pages().size()).append(" page(s)]\n");
            md.append("WCAG ").append(m.wcagSc()).append(" (").append(m.wcagName())
              .append(") · EN 301 549: ").append(m.en301549()).append("\n\n");
            md.append(m.plain()).append("\n\n");
            md.append("Examples:\n");
            for (Violation example : g.examples()) {
                md.append("- `").append(path(example.getPage().getUrl())).append("`  `")
                  .append(nullToDash(example.getSelector())).append("`  ")
                  .append(codeSnippet(example.getHtml())).append("\n");
            }
            if (g.helpUrl() != null) {
                md.append("\nHow to fix: ").append(g.helpUrl()).append("\n");
            }
            md.append("\n");
        }
        return md.toString();
    }

    private int score(Group g) {
        return IMPACT_WEIGHT.getOrDefault(g.impact(), 1) * g.pages().size();
    }

    private static long countDistinctPages(List<Violation> violations) {
        return violations.stream().map(v -> v.getPage().getUrl()).distinct().count();
    }

    private static String taskTitle(String ruleId) {
        // "color-contrast" -> "Fix: color contrast"
        return "Fix: " + ruleId.replace('-', ' ');
    }

    private static String path(String url) {
        try {
            URI u = URI.create(url);
            String p = u.getPath();
            return (p == null || p.isBlank()) ? "/" : p;
        } catch (Exception e) {
            return url;
        }
    }

    private static String nullToDash(String s) {
        return s == null ? "—" : s;
    }

    private static String codeSnippet(String html) {
        if (html == null) return "";
        String oneLine = html.replace("\n", " ").replace("`", "'");
        if (oneLine.length() > 120) oneLine = oneLine.substring(0, 120) + "…";
        return "`" + oneLine + "`";
    }
}
