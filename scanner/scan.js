#!/usr/bin/env node
'use strict';

/**
 * EAA scanner CLI.
 *
 * Usage:
 *   node scan.js --url https://example.com [--max-pages 30] [--concurrency 2] [--timeout 30000]
 *
 * Emits a single JSON document on stdout. All logging goes to stderr.
 * Exit codes: 0 = success, 1 = fatal error (JSON error object on stderr).
 */

const { chromium } = require('playwright');
const { AxeBuilder } = require('@axe-core/playwright');
const { discoverPages, USER_AGENT } = require('./lib/discover');

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const MAX_NODES_PER_RULE = 10;
const HARD_PAGE_CAP = 50;

function parseArgs(argv) {
  const args = { maxPages: 30, concurrency: 2, timeout: 30000 };
  for (let i = 2; i < argv.length; i++) {
    switch (argv[i]) {
      case '--url': args.url = argv[++i]; break;
      case '--max-pages': args.maxPages = parseInt(argv[++i], 10); break;
      case '--concurrency': args.concurrency = parseInt(argv[++i], 10); break;
      case '--timeout': args.timeout = parseInt(argv[++i], 10); break;
      default:
        throw new Error(`Unknown argument: ${argv[i]}`);
    }
  }
  if (!args.url) throw new Error('Missing required --url');
  if (!Number.isFinite(args.maxPages) || args.maxPages < 1) args.maxPages = 30;
  args.maxPages = Math.min(args.maxPages, HARD_PAGE_CAP);
  if (!Number.isFinite(args.concurrency) || args.concurrency < 1) args.concurrency = 2;
  args.concurrency = Math.min(args.concurrency, 4);
  return args;
}

const log = (msg) => process.stderr.write(`[scan] ${msg}\n`);

async function gotoSettled(page, url, timeout) {
  const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout });
  // Give SPAs a bounded chance to settle; never block longer than 10s extra.
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  return response;
}

async function scanPage(context, url, timeout) {
  const page = await context.newPage();
  try {
    const response = await gotoSettled(page, url, timeout);
    const status = response ? response.status() : null;

    const axe = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();

    const violations = axe.violations.map((v) => ({
      ruleId: v.id,
      impact: v.impact || 'minor',
      // axe tags: wcag143 -> SC 1.4.3, wcag1410 -> SC 1.4.10
      wcag: v.tags
        .filter((t) => /^wcag\d{3,4}$/.test(t))
        .map((t) => {
          const d = t.replace('wcag', '');
          return `${d[0]}.${d[1]}.${d.slice(2)}`;
        }),
      helpUrl: v.helpUrl,
      nodes: v.nodes.slice(0, MAX_NODES_PER_RULE).map((n) => ({
        selector: Array.isArray(n.target) ? n.target.join(' ') : String(n.target),
        html: (n.html || '').slice(0, 500),
      })),
    }));

    return { url, status, violations, error: null };
  } catch (e) {
    return { url, status: null, violations: [], error: e.message };
  } finally {
    await page.close().catch(() => {});
  }
}

async function main() {
  const args = parseArgs(process.argv);
  log(`starting scan of ${args.url} (max ${args.maxPages} pages)`);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ userAgent: USER_AGENT });

  try {
    // Link extraction for BFS uses a real page so SPA links are visible.
    const extractLinks = async (url) => {
      const page = await context.newPage();
      try {
        await gotoSettled(page, url, args.timeout);
        return await page.$$eval('a[href]', (as) => as.map((a) => a.href));
      } finally {
        await page.close().catch(() => {});
      }
    };

    const { urls } = await discoverPages({
      startUrl: args.url,
      maxPages: args.maxPages,
      extractLinks,
      log,
    });
    log(`scanning ${urls.length} page(s) with concurrency ${args.concurrency}`);

    const results = [];
    let index = 0;
    async function worker(id) {
      while (index < urls.length) {
        const myIndex = index++;
        const url = urls[myIndex];
        log(`[worker ${id}] ${myIndex + 1}/${urls.length} ${url}`);
        results[myIndex] = await scanPage(context, url, args.timeout);
      }
    }
    await Promise.all(
      Array.from({ length: Math.min(args.concurrency, urls.length) }, (_, i) => worker(i + 1))
    );

    // axe-core version: read from the dependency's package metadata.
    let axeVersion = 'unknown';
    try {
      axeVersion = require('axe-core/package.json').version;
    } catch { /* optional */ }

    const output = {
      startUrl: args.url,
      scannedAt: new Date().toISOString(),
      engine: { name: 'axe-core', version: axeVersion },
      pagesRequested: args.maxPages,
      pages: results,
    };
    process.stdout.write(JSON.stringify(output));
    log('done');
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

main().catch((e) => {
  process.stderr.write(JSON.stringify({ error: e.message }) + '\n');
  process.exit(1);
});
