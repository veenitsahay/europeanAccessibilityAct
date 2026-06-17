package com.vulmo.a11y.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spawns the Node scanner CLI and returns its parsed JSON output.
 * stdout = result JSON; stderr = progress logs (forwarded to our log).
 */
@Component
public class ScannerRunner {

    private static final Logger log = LoggerFactory.getLogger(ScannerRunner.class);

    private final ObjectMapper objectMapper;
    private final String command;
    private final String script;
    private final long timeoutMinutes;

    public ScannerRunner(ObjectMapper objectMapper,
                         @Value("${app.scanner.command}") String command,
                         @Value("${app.scanner.script}") String script,
                         @Value("${app.scanner.timeout-minutes}") long timeoutMinutes) {
        this.objectMapper = objectMapper;
        this.command = command;
        this.script = script;
        this.timeoutMinutes = timeoutMinutes;
    }

    public ScannerResult.Root run(String url, int maxPages) throws IOException, InterruptedException {
        List<String> cmd = List.of(command, script, "--url", url, "--max-pages", String.valueOf(maxPages));
        log.info("Launching scanner: {}", String.join(" ", cmd));

        Process process = new ProcessBuilder(cmd).start();

        // Read stdout and stderr concurrently to avoid pipe-buffer deadlock.
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        Thread stdoutPump = pump(process.getInputStream(), stdout);
        StringBuilder stderrTail = new StringBuilder();
        Thread stderrPump = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[scanner] {}", line);
                    stderrTail.append(line).append('\n');
                    if (stderrTail.length() > 4000) {
                        stderrTail.delete(0, stderrTail.length() - 4000);
                    }
                }
            } catch (IOException ignored) { }
        });
        stdoutPump.start();
        stderrPump.start();

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Scanner timed out after " + timeoutMinutes + " minutes");
        }
        stdoutPump.join(5000);
        stderrPump.join(5000);

        if (process.exitValue() != 0) {
            throw new IOException("Scanner exited with code " + process.exitValue()
                    + ". Last output: " + stderrTail);
        }
        byte[] json = stdout.toByteArray();
        if (json.length == 0) {
            throw new IOException("Scanner produced no output. Last logs: " + stderrTail);
        }
        return objectMapper.readValue(json, ScannerResult.Root.class);
    }

    private Thread pump(InputStream in, ByteArrayOutputStream out) {
        return new Thread(() -> {
            try {
                in.transferTo(out);
            } catch (IOException ignored) { }
        });
    }
}
