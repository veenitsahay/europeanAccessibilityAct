package com.vulmo.a11y.web;

import com.vulmo.a11y.domain.Scan;
import com.vulmo.a11y.repo.ScanRepository;
import com.vulmo.a11y.repo.ViolationRepository;
import com.vulmo.a11y.report.StatementService;
import com.vulmo.a11y.report.TaskListService;
import com.vulmo.a11y.service.ScanService;
import com.vulmo.a11y.web.dto.CreateScanRequest;
import com.vulmo.a11y.web.dto.ScanSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;
    private final ScanRepository scanRepository;
    private final ViolationRepository violationRepository;
    private final TaskListService taskListService;
    private final StatementService statementService;

    public ScanController(ScanService scanService,
                          ScanRepository scanRepository,
                          ViolationRepository violationRepository,
                          TaskListService taskListService,
                          StatementService statementService) {
        this.scanService = scanService;
        this.scanRepository = scanRepository;
        this.violationRepository = violationRepository;
        this.taskListService = taskListService;
        this.statementService = statementService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateScanRequest request) {
        Scan scan = scanService.create(request.url(), request.maxPages(),
                request.orgName(), request.siteName(), request.contactEmail());
        scanService.execute(scan.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", scan.getId()));
    }

    @GetMapping("/{id}")
    public ScanSummaryResponse get(@PathVariable UUID id) {
        Scan scan = scanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found"));
        long violations = violationRepository.findAllByScanId(id).size();
        return ScanSummaryResponse.from(scan, violations);
    }

    @GetMapping(value = "/{id}/tasks", produces = "text/markdown;charset=UTF-8")
    public String tasks(@PathVariable UUID id) {
        requireDone(id);
        return taskListService.renderMarkdown(id);
    }

    @GetMapping(value = "/{id}/statement", produces = "text/markdown;charset=UTF-8")
    public String statement(@PathVariable UUID id) {
        requireDone(id);
        return statementService.renderMarkdown(id);
    }

    private void requireDone(UUID id) {
        Scan scan = scanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found"));
        if (scan.getStatus() != Scan.Status.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Scan status is " + scan.getStatus() + "; report available once DONE");
        }
    }
}
