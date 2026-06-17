# EAA Scan & Statement Service

Scans a public website for WCAG 2.1 AA issues and produces two regulator-shaped outputs:

1. **A prioritized developer task list** — issues grouped by rule, scored by
   severity × pages affected, with selectors, HTML snippets, and fix links.
2. **A draft EAA accessibility statement** — conformance status, non-accessible
   content mapped to WCAG / EN 301 549 clauses, and a methodology section.

Two honesty rules are enforced in code: the statement never claims "fully
conformant" (automated testing cannot prove it), and the methodology paragraph
(tool, version, date, coverage caveat) is always included.

## Architecture

```
Spring Boot API (Java 21, :8081)  ──spawns──▶  Node scanner CLI
  REST + Postgres + reports                     Playwright + axe-core
```

## Prerequisites

- Java 21, Maven
- Node.js 20+
- Docker (for Postgres)

## Setup

```bash
# 1. Database (host port 5433; deliberately avoids your vulmo dev db on 5432)
docker compose up -d

# 2. Scanner dependencies + headless Chromium (~400 MB of system deps on Linux)
cd scanner
npm install
npx playwright install chromium --with-deps
cd ..

# 3. Backend (Flyway migrates on first boot)
cd backend
mvn spring-boot:run
```

The backend runs on **http://localhost:8081** (8081 so it can run beside the
vulmo backend on 8080).

## Usage

```bash
# Start a scan (202 Accepted; runs async)
curl -s -X POST http://localhost:8081/api/scans \
  -H 'Content-Type: application/json' \
  -d '{
        "url": "https://example.com",
        "maxPages": 20,
        "orgName": "Example Ltd",
        "siteName": "Example Shop",
        "contactEmail": "accessibility@example.com"
      }'
# -> {"id":"<scan-id>"}

# Poll status (PENDING -> RUNNING -> DONE/FAILED; a 20-page scan takes 2-5 min)
curl -s http://localhost:8081/api/scans/<scan-id>

# The two deliverables (Markdown)
curl -s http://localhost:8081/api/scans/<scan-id>/tasks
curl -s http://localhost:8081/api/scans/<scan-id>/statement
```

You can also run the scanner standalone:

```bash
node scanner/scan.js --url https://example.com --max-pages 10 > result.json
```

## Configuration (env vars)

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/eaascan` | Database |
| `SPRING_DATASOURCE_USERNAME` / `..._PASSWORD` | `postgres` / `welcome` | Credentials |
| `APP_SCANNER_COMMAND` | `node` | Node binary |
| `APP_SCANNER_SCRIPT` | `../scanner/scan.js` | Scanner path (relative to where the backend runs) |
| `SERVER_PORT` | `8081` | API port |

Server-side caps regardless of request: 50 pages max, 15-minute scan timeout,
one concurrent scan (Chromium wants 300–500 MB; raise the pool in
`AsyncConfig` only on 4 GB+ machines).

## Safety

Both the API (`SsrfGuard.java`) and the scanner (`lib/ssrf.js`) refuse URLs
resolving to loopback, private, link-local (incl. the EC2 metadata endpoint),
carrier-grade NAT, or unique-local ranges. Only scan sites you own or have
permission to scan; the crawler respects `robots.txt` and identifies itself.

## Honest limitations

- Automated testing covers a **subset** of WCAG 2.1 AA. Full conformance
  requires manual screen-reader and keyboard testing — the generated statement
  says so explicitly, by design.
- The statement's enforcement-body section is a placeholder; it varies by EU
  member state.
- Redirect-based SSRF (a scanned page redirecting to an internal host) is
  mitigated by same-origin crawling but not fully closed; do not run the
  scanner on a host with sensitive internal services reachable.

## Deliberately deferred (MVP scope)

UI, auth/users, CI/GitHub-Action integration, scheduled re-scans and
monitoring, scan diffing, PDF export, multi-tenancy, billing,
per-country enforcement-body data, manual-audit workflow.

## Project layout

```
eaa-scan/
├── docker-compose.yml          Postgres 16 on host port 5433
├── scanner/                    Node CLI (Playwright + axe-core)
│   ├── scan.js                 entry point
│   └── lib/{discover,ssrf}.js  crawling + SSRF guard
└── backend/                    Spring Boot 3.3 / Java 21
    └── src/main/
        ├── java/com/vulmo/a11y/
        │   ├── web/            controller, DTOs, error handler
        │   ├── service/        scan orchestration, process runner, SSRF guard
        │   ├── report/         task list + statement generators, WCAG mapping
        │   └── domain|repo/    entities + repositories
        └── resources/
            ├── db/migration/V1__init.sql
            ├── mapping/axe-wcag-en301549.json   (39 rules mapped)
            └── templates/statement.md
```
