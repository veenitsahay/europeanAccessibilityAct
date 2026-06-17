'use strict';

const dns = require('dns').promises;
const net = require('net');

/**
 * SSRF guard: refuses URLs whose host resolves to private, loopback,
 * link-local or otherwise internal address space. The EC2 metadata
 * endpoint (169.254.169.254) is the classic cloud target.
 */

function ipv4ToLong(ip) {
  return ip.split('.').reduce((acc, oct) => (acc << 8) + parseInt(oct, 10), 0) >>> 0;
}

function inCidr4(ip, cidr) {
  const [range, bitsStr] = cidr.split('/');
  const bits = parseInt(bitsStr, 10);
  const mask = bits === 0 ? 0 : (~0 << (32 - bits)) >>> 0;
  return (ipv4ToLong(ip) & mask) === (ipv4ToLong(range) & mask);
}

const BLOCKED_V4 = [
  '0.0.0.0/8',
  '10.0.0.0/8',
  '100.64.0.0/10',   // carrier-grade NAT
  '127.0.0.0/8',
  '169.254.0.0/16',  // link-local incl. EC2 metadata
  '172.16.0.0/12',
  '192.168.0.0/16',
];

function isBlockedAddress(addr) {
  if (net.isIPv4(addr)) {
    return BLOCKED_V4.some((cidr) => inCidr4(addr, cidr));
  }
  if (net.isIPv6(addr)) {
    const a = addr.toLowerCase();
    if (a === '::' || a === '::1') return true;            // unspecified / loopback
    if (a.startsWith('fe80')) return true;                 // link-local
    if (a.startsWith('fc') || a.startsWith('fd')) return true; // unique-local fc00::/7
    if (a.startsWith('::ffff:')) {                         // IPv4-mapped
      const v4 = a.replace('::ffff:', '');
      if (net.isIPv4(v4)) return isBlockedAddress(v4);
    }
    return false;
  }
  return true; // unparseable -> block
}

async function assertSafeUrl(rawUrl) {
  let url;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new Error(`Invalid URL: ${rawUrl}`);
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error(`Blocked scheme: ${url.protocol}`);
  }
  const host = url.hostname;
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.internal')) {
    throw new Error(`Blocked host: ${host}`);
  }
  // Literal IP in the URL
  if (net.isIP(host)) {
    if (isBlockedAddress(host)) throw new Error(`Blocked address: ${host}`);
    return url;
  }
  // Resolve and check every address
  let records;
  try {
    records = await dns.lookup(host, { all: true });
  } catch (e) {
    throw new Error(`DNS lookup failed for ${host}: ${e.message}`);
  }
  for (const r of records) {
    if (isBlockedAddress(r.address)) {
      throw new Error(`Host ${host} resolves to blocked address ${r.address}`);
    }
  }
  return url;
}

module.exports = { assertSafeUrl, isBlockedAddress };
