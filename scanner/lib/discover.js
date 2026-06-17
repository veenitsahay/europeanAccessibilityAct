'use strict';

const { assertSafeUrl } = require('./ssrf');

const USER_AGENT = 'VulmoScan/0.1 (+accessibility-compliance-scan; contact site owner before scanning third-party sites)';
const BINARY_EXT = /\.(pdf|jpe?g|png|gif|webp|svg|ico|zip|gz|tar|rar|7z|mp[34]|avi|mov|webm|woff2?|ttf|eot|css|js|json|xml|txt)(\?.*)?$/i;

function normalize(rawUrl, origin) {
  try {
    const u = new URL(rawUrl, origin);
    if (u.origin !== origin) return null;          // same-origin only
    u.hash = '';
    u.search = '';                                  // strip query for dedupe
    if (BINARY_EXT.test(u.pathname)) return null;
    return u.toString();
  } catch {
    return null;
  }
}

async function fetchText(url, timeoutMs = 10000) {
  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': USER_AGENT },
      signal: AbortSignal.timeout(timeoutMs),
      redirect: 'follow',
    });
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

/** Minimal robots.txt: collect Disallow rules under User-agent: * */
async function loadRobots(origin) {
  const txt = await fetchText(`${origin}/robots.txt`);
  const disallow = [];
  if (!txt) return { disallow };
  let applies = false;
  for (const lineRaw of txt.split('\n')) {
    const line = lineRaw.replace(/#.*$/, '').trim();
    if (!line) continue;
    const [keyRaw, ...rest] = line.split(':');
    const key = keyRaw.trim().toLowerCase();
    const value = rest.join(':').trim();
    if (key === 'user-agent') {
      applies = value === '*';
    } else if (applies && key === 'disallow' && value) {
      disallow.push(value);
    }
  }
  return { disallow };
}

function isAllowed(url, origin, robots) {
  const path = url.slice(origin.length) || '/';
  return !robots.disallow.some((rule) => path.startsWith(rule));
}

/** Try sitemap.xml; returns normalized, robots-allowed URLs (possibly empty). */
async function fromSitemap(origin, robots, maxPages) {
  const xml = await fetchText(`${origin}/sitemap.xml`, 15000);
  if (!xml) return [];
  const urls = [];
  const re = /<loc>\s*([^<\s]+)\s*<\/loc>/gi;
  let m;
  while ((m = re.exec(xml)) !== null && urls.length < maxPages) {
    const n = normalize(m[1], origin);
    if (n && isAllowed(n, origin, robots) && !urls.includes(n)) urls.push(n);
  }
  return urls;
}

/**
 * Discover pages to scan. Strategy: sitemap first, BFS fallback.
 * BFS uses a callback `extractLinks(url) -> string[]` supplied by the caller
 * (implemented with Playwright so SPA-rendered links are visible).
 */
async function discoverPages({ startUrl, maxPages, extractLinks, log }) {
  const start = await assertSafeUrl(startUrl);
  const origin = start.origin;
  const robots = await loadRobots(origin);

  const sitemapUrls = await fromSitemap(origin, robots, maxPages);
  if (sitemapUrls.length > 0) {
    log(`sitemap.xml found: ${sitemapUrls.length} URLs (capped at ${maxPages})`);
    // Make sure the start URL itself is included and first
    const startNorm = normalize(start.toString(), origin);
    const list = [startNorm, ...sitemapUrls.filter((u) => u !== startNorm)];
    return { origin, robots, urls: list.slice(0, maxPages) };
  }

  log('no usable sitemap.xml; falling back to same-origin BFS crawl');
  const startNorm = normalize(start.toString(), origin);
  const queue = [startNorm];
  const seen = new Set(queue);
  const result = [];

  while (queue.length > 0 && result.length < maxPages) {
    const url = queue.shift();
    if (!isAllowed(url, origin, robots)) continue;
    result.push(url);
    let links = [];
    try {
      links = await extractLinks(url);
    } catch (e) {
      log(`link extraction failed for ${url}: ${e.message}`);
    }
    for (const raw of links) {
      const n = normalize(raw, origin);
      if (n && !seen.has(n) && isAllowed(n, origin, robots)) {
        seen.add(n);
        queue.push(n);
      }
    }
  }
  return { origin, robots, urls: result };
}

module.exports = { discoverPages, USER_AGENT };
