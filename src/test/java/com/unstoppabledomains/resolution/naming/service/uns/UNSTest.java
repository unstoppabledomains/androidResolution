package com.unstoppabledomains.resolution.naming.service.uns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unstoppabledomains.config.network.model.Network;
import com.unstoppabledomains.exceptions.ns.NamingServiceException;
import com.unstoppabledomains.resolution.naming.service.NSConfig;
import com.unstoppabledomains.resolution.contracts.DefaultProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UNSTest {
    @Mock
    private L2Resolver resolver;

    @InjectMocks
    private UNS uns = new UNS(new UNSConfig(new NSConfig(Network.MAINNET, "example.com", "0x01"),
            new NSConfig(Network.MATIC_MAINNET, "example.com", "0x02")), new DefaultProvider(), resolver);

    @Test
    public void batchOwnersPrefersL2Owners() throws NamingServiceException {
        Map<String, String> expected = new HashMap<>();
        expected.put("domain1", "owner5");
        expected.put("domain2", "owner2");
        expected.put("domain3", "owner3");
        expected.put("domain4", "owner4");

        Map<String, String> l1Owners = new HashMap<>();
        l1Owners.put("domain1", "owner1");
        l1Owners.put("domain2", "owner2");
        l1Owners.put("domain3", "owner3");
        Map<String, String> l2Owners = new HashMap<>();
        l2Owners.put("domain4", "owner4");
        l2Owners.put("domain1", "owner5");
        l2Owners.put("domain3", "owner3");
        List<Map<String, String>> value = Arrays.asList(l1Owners, l2Owners);

        when(resolver.<Map<String, String>>resolveOnBothLayers(any())).thenReturn(value);

        Map<String, String> domainOwners = uns.batchOwners(new ArrayList<>());

        verify(resolver).resolveOnBothLayers(any());
        assertEquals(expected.entrySet(), domainOwners.entrySet());
    }

    @Test
    public void getTokensOwnedByReturnsUniqueTokens() throws NamingServiceException {
        List<String> expected = Arrays.asList("domain1", "domain2", "domain3", "domain4");

        List<String> l1Domains = Arrays.asList("domain1", "domain2", "domain3");
        List<String> l2Domains = Arrays.asList("domain2", "domain3", "domain4");

        when(resolver.<List<String>>resolveOnBothLayers(any())).thenReturn(Arrays.asList(l1Domains, l2Domains));

        List<String> domains = uns.getTokensOwnedBy("owner");

        verify(resolver).resolveOnBothLayers(any());
        expected.sort(Comparator.naturalOrder());
        domains.sort(Comparator.naturalOrder());
        assertEquals(expected, domains);
    }
}
