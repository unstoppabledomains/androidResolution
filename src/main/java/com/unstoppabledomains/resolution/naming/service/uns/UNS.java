package com.unstoppabledomains.resolution.naming.service.uns;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unstoppabledomains.config.network.model.Network;
import com.unstoppabledomains.exceptions.dns.DnsException;
import com.unstoppabledomains.exceptions.ns.NamingServiceException;
import com.unstoppabledomains.resolution.contracts.interfaces.IProvider;
import com.unstoppabledomains.resolution.dns.DnsRecord;
import com.unstoppabledomains.resolution.dns.DnsRecordsType;
import com.unstoppabledomains.resolution.naming.service.NamingService;
import com.unstoppabledomains.resolution.naming.service.NamingServiceType;

public class UNS implements NamingService {
    private L2Resolver resolver;
    private UNSInternal unsl1;
    private UNSInternal unsl2;

    public UNS(UNSConfig config, IProvider provider) {
        this(config, provider, new L2Resolver());
    }

    protected UNS(UNSConfig config, IProvider provider, L2Resolver resolver) {
        this.resolver = resolver;
        unsl1 = new UNSInternal(UNSLocation.Layer1, config.getLayer1(), provider);
        unsl2 = new UNSInternal(UNSLocation.Layer2, config.getLayer2(), provider);
    }

    @Override
    public Network getNetwork() {
        return unsl1.getNetwork();
    }

    @Override
    public String getProviderUrl() {
        return unsl1.getProviderUrl();
    }

    @Override
    public String getContractAddress() {
        return unsl1.getContractAddress();
    }

    public Network getNetwork(UNSLocation layer) {
        return getNSForLayer(layer).getNetwork();
    }

    public String getProviderUrl(UNSLocation layer) {
        return getNSForLayer(layer).getProviderUrl();
    }

    public String getContractAddress(UNSLocation layer) {
        return getNSForLayer(layer).getContractAddress();
    }

    private NamingService getNSForLayer(UNSLocation layer) {
        switch (layer) {
            case Layer1:
                return unsl1;
            case Layer2:
                return unsl2;
        }
        return unsl1;
    }

    @Override
    public NamingServiceType getType() {
        return NamingServiceType.UNS;
    }

    @Override
    public Boolean isSupported(String domain) throws NamingServiceException {
        return unsl1.isSupported(domain);
    }

    @Override
    public String getNamehash(String domain) throws NamingServiceException {
        return unsl1.getNamehash(domain);
    }

    @Override
    public String getRecord(String domain, String recordKey) throws NamingServiceException {
        return resolver.resolve(ResolutionMethods.<String>builder()
            .l1Func(() -> {
                return unsl1.getRecord(domain, recordKey);
            })
            .l2Func(() -> {
                return unsl2.getRecord(domain, recordKey);
            }).build()
        );
    }

    @Override
    public String getOwner(String domain) throws NamingServiceException {
        return resolver.resolve(ResolutionMethods.<String>builder()
            .l1Func(() -> {
                return unsl1.getOwner(domain);
            })
            .l2Func(() -> {
                return unsl2.getOwner(domain);
            }).build()
        );
    }

    @Override
    public Map<String, String> batchOwners(List<String> domain) throws NamingServiceException {
        List<Map<String, String>> results = resolver.resolveOnBothLayers(ResolutionMethods.<Map<String, String>>builder()
            .l1Func(() -> {
                return unsl1.batchOwners(domain);
            })
            .l2Func(() -> {
                return unsl2.batchOwners(domain);
            }).build());
        Map<String, String> result = results.get(0);
        results.get(1).forEach((k, v) -> {
            if (v != null) {
                result.merge(k, v, (v1, v2) -> v2);
            }
        });
        return result;
    }

    @Override
    public List<DnsRecord> getDns(String domain, List<DnsRecordsType> types)
            throws NamingServiceException, DnsException {
        return resolver.resolve(ResolutionMethods.<List<DnsRecord>>builder()
            .l1Func(() -> {
                return unsl1.getDns(domain, types);
            })
            .l2Func(() -> {
                return unsl2.getDns(domain, types);
            }).build()
        );
    }

    @Override
    public String getTokenUri(BigInteger tokenID) throws NamingServiceException {
        return resolver.resolve(ResolutionMethods.<String>builder()
            .l1Func(() -> {
                return unsl1.getTokenUri(tokenID);
            })
            .l2Func(() -> {
                return unsl2.getTokenUri(tokenID);
            }).build()
        );
    }

    @Override
    public String getDomainName(BigInteger tokenID) throws NamingServiceException {
        return resolver.resolve(ResolutionMethods.<String>builder()
            .l1Func(() -> {
                return unsl1.getDomainName(tokenID);
            })
            .l2Func(() -> {
                return unsl2.getDomainName(tokenID);
            }).build()
        );
    }

    @Override
    public List<String> getTokensOwnedBy(String owner) throws NamingServiceException {
        List<List<String>> results = resolver.resolveOnBothLayers(ResolutionMethods.<List<String>>builder()
            .l1Func(() -> {
                return unsl1.getTokensOwnedBy(owner);
            })
            .l2Func(() -> {
                return unsl2.getTokensOwnedBy(owner);
            }).build()
        );

        Set<String> result = new HashSet<>();
        results.forEach(list -> result.addAll(list));
        return new ArrayList<String>(result);
    }
}
