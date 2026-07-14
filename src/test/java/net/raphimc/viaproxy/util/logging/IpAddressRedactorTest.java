package net.raphimc.viaproxy.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpAddressRedactorTest {

    @Test
    void redactsIpv4AndIpv6Addresses() {
        assertEquals(
                "peer=REDACTED_IP upstream=REDACTED_IP",
                IpAddressRedactor.redact("peer=192.168.10.24 upstream=2001:db8::8")
        );
    }

    @Test
    void leavesUnrelatedTextUntouched() {
        assertEquals("ViaProxy failed to translate packet 42", IpAddressRedactor.redact("ViaProxy failed to translate packet 42"));
    }

}
