package com.vulmo.a11y.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
public class MappingService {

    public record RuleMapping(String wcagSc, String wcagName, String en301549, String plain) { }

    private final Map<String, RuleMapping> mappings;

    public MappingService(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("mapping/axe-wcag-en301549.json").getInputStream()) {
            this.mappings = objectMapper.readValue(in, new TypeReference<Map<String, RuleMapping>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load WCAG mapping table", e);
        }
    }

    /**
     * Mapping for a rule. Unknown rules get a generic fallback built from the
     * WCAG criteria the scanner attached to the violation, so new axe rules
     * degrade gracefully instead of disappearing from reports.
     */
    public RuleMapping forRule(String ruleId, String wcagScFromViolation) {
        RuleMapping known = mappings.get(ruleId);
        if (known != null) return known;
        String sc = (wcagScFromViolation == null || wcagScFromViolation.isBlank())
                ? "unmapped" : wcagScFromViolation.split(",")[0];
        String en = sc.equals("unmapped") ? "see WCAG mapping" : "9." + sc;
        return new RuleMapping(sc, "WCAG success criterion " + sc, en,
                "Accessibility issue detected by rule '" + ruleId
                        + "'. See the linked guidance for details.");
    }
}
