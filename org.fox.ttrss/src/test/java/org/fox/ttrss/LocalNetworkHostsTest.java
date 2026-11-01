package org.fox.ttrss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class LocalNetworkHostsTest {
    private static boolean isLocal(String address) throws UnknownHostException {
        return LocalNetworkHosts.isLocalAddress(InetAddress.getByName(address));
    }

    @Test
    public void privateIpv4RangesAreLocal() throws UnknownHostException {
        assertTrue(isLocal("10.1.2.3"));
        assertTrue(isLocal("172.16.0.1"));
        assertTrue(isLocal("172.31.255.254"));
        assertTrue(isLocal("192.168.2.55"));
    }

    @Test
    public void linkLocalCgnatAndMulticastAreLocal() throws UnknownHostException {
        assertTrue(isLocal("169.254.1.1"));
        assertTrue(isLocal("100.64.0.1"));
        assertTrue(isLocal("100.127.255.254"));
        assertTrue(isLocal("224.0.0.251"));
    }

    @Test
    public void localIpv6RangesAreLocal() throws UnknownHostException {
        assertTrue(isLocal("fe80::1"));
        assertTrue(isLocal("fd00::1"));
        assertTrue(isLocal("ff02::fb"));
    }

    @Test
    public void publicAddressesAreNotLocal() throws UnknownHostException {
        assertFalse(isLocal("8.8.8.8"));
        assertFalse(isLocal("172.32.0.1"));
        assertFalse(isLocal("100.128.0.1"));
        assertFalse(isLocal("2001:4860:4860::8888"));
    }

    @Test
    public void localUrlsAreDetected() {
        assertTrue(LocalNetworkHosts.isLocalUrl("http://192.168.1.10/tt-rss"));
        assertTrue(LocalNetworkHosts.isLocalUrl("https://[fd00::1]:8443/tt-rss"));
        assertTrue(LocalNetworkHosts.isLocalUrl("https://tt-rss.LOCAL/"));
    }

    @Test
    public void publicAndInvalidUrlsAreNotLocal() {
        assertFalse(LocalNetworkHosts.isLocalUrl("https://8.8.8.8/tt-rss"));
        assertFalse(LocalNetworkHosts.isLocalUrl("not a url"));
    }
}
