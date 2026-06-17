package com.vulmo.a11y.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Defence in depth: the Node scanner applies the same checks, but the API
 * validates before ever spawning a process. Rejects URLs whose host resolves
 * to loopback, private, link-local (incl. the EC2 metadata endpoint
 * 169.254.169.254), carrier-grade NAT, or unique-local address space.
 */
@Component
public class SsrfGuard {

    public void assertSafe(String rawUrl) {
        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + rawUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host");
        }
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".internal")) {
            throw new IllegalArgumentException("Blocked host: " + host);
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("DNS lookup failed for host: " + host);
        }
        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new IllegalArgumentException(
                        "Host resolves to a blocked address range: " + addr.getHostAddress());
            }
        }
    }

    private boolean isBlocked(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            // 100.64.0.0/10 carrier-grade NAT (not covered by isSiteLocalAddress)
            return first == 100 && second >= 64 && second <= 127;
        }
        if (b.length == 16) {
            int first = b[0] & 0xFF;
            // fc00::/7 unique-local
            return first == 0xFC || first == 0xFD;
        }
        return false;
    }
}
