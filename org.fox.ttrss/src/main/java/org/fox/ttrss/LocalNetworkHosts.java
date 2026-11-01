package org.fox.ttrss;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

import okhttp3.HttpUrl;

/**
 * Detects servers covered by the ACCESS_LOCAL_NETWORK permission, see
 * https://developer.android.com/privacy-and-security/local-network-definition
 */
class LocalNetworkHosts {
    private LocalNetworkHosts() {
    }

    /** May perform a DNS lookup, so this must not be called on the main thread. */
    static boolean isLocalUrl(@NonNull String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);

        if (httpUrl == null)
            return false;

        String host = httpUrl.host();

        if (host.toLowerCase(Locale.ROOT).endsWith(".local"))
            return true;

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isLocalAddress(address))
                    return true;
            }
        } catch (UnknownHostException e) {
            return false;
        }

        return false;
    }

    static boolean isLocalAddress(@NonNull InetAddress address) {
        byte[] bytes = address.getAddress();

        boolean isCgnat = bytes.length == 4 && bytes[0] == 100 && (bytes[1] & 0xC0) == 0x40;
        boolean isUniqueLocal = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;

        return address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isMulticastAddress() || isCgnat || isUniqueLocal;
    }
}
