package com.unstoppabledomains.resolution;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unstoppabledomains.config.network.NetworkConfigLoader;
import com.unstoppabledomains.config.network.model.Network;
import com.unstoppabledomains.exceptions.dns.DnsException;
import com.unstoppabledomains.exceptions.ns.NSExceptionCode;
import com.unstoppabledomains.exceptions.ns.NSExceptionParams;
import com.unstoppabledomains.exceptions.ns.NamingServiceException;
import com.unstoppabledomains.resolution.contracts.DefaultProvider;
import com.unstoppabledomains.resolution.contracts.JsonProvider;
import com.unstoppabledomains.resolution.contracts.interfaces.IProvider;
import com.unstoppabledomains.resolution.dns.DnsRecord;
import com.unstoppabledomains.resolution.dns.DnsRecordsType;
import com.unstoppabledomains.resolution.naming.service.ENS;
import com.unstoppabledomains.resolution.naming.service.NSConfig;
import com.unstoppabledomains.resolution.naming.service.NamingService;
import com.unstoppabledomains.resolution.naming.service.NamingServiceType;
import com.unstoppabledomains.resolution.naming.service.ZNS;
import com.unstoppabledomains.resolution.naming.service.uns.UNS;
import com.unstoppabledomains.resolution.naming.service.uns.UNSConfig;
import com.unstoppabledomains.util.Utilities;

public class Resolution implements DomainResolution {
    private Map<NamingServiceType, NamingService> services;

    /**
     * Use {@link ResolutionBuilder} methods to override default configs
     * or get Resolution object with default settings
     * by calling {@link ResolutionBuilder#build()}
     *
     * @return builder object
     */
    public static ResolutionBuilder builder() {
        return new ResolutionBuilder(new ResolutionBuilderConnector());
    }

    /**
     * Create resolution object with default config:
     * <a href="https://infura.io">infura</a> blockchain provider for ENS and UNS and
     * <a href="https://zilliqa.com">zilliqa</a> for ZNS
     */
    public Resolution() {
        IProvider provider = new DefaultProvider();
        services = getServices(provider);
    }

    private Resolution(Map<NamingServiceType, NamingService> services) {
        this.services = services;
    }

    @Override
    public boolean isSupported(String domain) throws NamingServiceException {
        for (NamingService service: services.values()) {
            if (service.isSupported(domain)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Network getNetwork(NamingServiceType type) {
        NamingService service = services.get(type);
        return service.getNetwork();
    }

    @Override
    public String getRecord(String domain, String recordKey) throws NamingServiceException {
        domain = normalizeDomain(domain);
        return findService(domain).getRecord(domain, recordKey);
    }

    @Override
    public String getAddress(String domain, String ticker) throws NamingServiceException {
        String recordKey = "crypto." + ticker.toUpperCase() + ".address";
        try {
            return getRecord(domain, recordKey);
        } catch(NamingServiceException exception) {
            if (exception.getCode() == NSExceptionCode.RecordNotFound) {
                throw new NamingServiceException(NSExceptionCode.UnknownCurrency, 
                    new NSExceptionParams("d|c", domain, ticker));
            }
            throw exception;
        }
    }

    @Override
    public String getMultiChainAddress(String domain, String ticker, String chain) throws NamingServiceException {
        NamingService service = findService(domain);
        if (service.getType() == NamingServiceType.ENS) {
            throw new NamingServiceException(NSExceptionCode.NotImplemented,
                new NSExceptionParams("d|m", domain, "getMultiChainAddress"));
        }
        String recordKey = "crypto." + ticker.toUpperCase() + ".version." + chain.toUpperCase() + ".address";
        return getRecord(domain, recordKey);
    }

    @Override
    public String getNamehash(String domain) throws NamingServiceException {
        domain = normalizeDomain(domain);
        NamingService service = findService(domain);
        return service.getNamehash(domain);
    }

    @Override
    public String getIpfsHash(String domain) throws NamingServiceException {
        String recordKey = "dweb.ipfs.hash";
        return getRecord(domain, recordKey);
    }

    @Override
    public String getEmail(String domain) throws NamingServiceException {
        String recordKey = "whois.email.value";
        return getRecord(domain, recordKey);
    }

    @Override
    public String getOwner(String domain) throws NamingServiceException {
        domain = normalizeDomain(domain);
        NamingService service = findService(domain);
        return service.getOwner(domain);
    }

    @Override
    public Map<String, String> getBatchOwners(List<String> domains) throws NamingServiceException {
        NamingService service = findService(domains.get(0));
        boolean inconsistentDomainArray = domains.stream().allMatch(d-> {
            try {
                return (findService(d) == service);
            } catch (NamingServiceException e) {
                return false;
            }
        });
        if (!inconsistentDomainArray) {
            throw new NamingServiceException(NSExceptionCode.InconsistentDomainArray);
        }

        return service.batchOwners(domains);
    }

    @Override
    public List<DnsRecord> getDns(String domain, List<DnsRecordsType> types) throws NamingServiceException, DnsException {
        domain = normalizeDomain(domain);
        NamingService service = findService(domain);
        return service.getDns(domain, types);
    }

    @Override
    public List<String> getTokensOwnedBy(String address, NamingServiceType service) throws NamingServiceException {
        if (service != NamingServiceType.UNS) {
            throw new NamingServiceException(NSExceptionCode.NotImplemented, new NSExceptionParams("m|n", "getTokensOwnedBy", service.toString()));
        }
        return services.get(service).getTokensOwnedBy(address);
    }

    @Override
    public String getTokenURI(String domain) throws NamingServiceException {
        domain = normalizeDomain(domain);
        try {
            NamingService service = findService(domain);
            String namehash = service.getNamehash(domain);
            BigInteger tokenId = Utilities.namehashToTokenID(namehash);
            return service.getTokenUri(tokenId);
        } catch (NamingServiceException e) {
            if (e.getCode() == NSExceptionCode.UnregisteredDomain) {
                throw new NamingServiceException(NSExceptionCode.UnregisteredDomain, new NSExceptionParams("d|m", domain, "tokenURI"), e);
            }
            throw e;
        }
    }

    @Override
    public TokenUriMetadata getTokenURIMetadata(String domain) throws NamingServiceException {
        String tokenURI = getTokenURI(domain);
        return getMetadataFromTokenURI(tokenURI);
    }

    @Override
    public String unhash(String hash, NamingServiceType serviceType) throws NamingServiceException {
        NamingService service = services.get(serviceType);
        BigInteger tokenId = Utilities.namehashToTokenID(hash);
        String domainName = service.getDomainName(tokenId);
        if (!service.getNamehash(domainName).equals(hash)) {
            throw new NamingServiceException(NSExceptionCode.UnknownError, new NSExceptionParams("m", "unhash"));
        }
        return domainName;
    }

    private TokenUriMetadata getMetadataFromTokenURI(String tokenURI) throws NamingServiceException {
        try {
            JsonProvider provider = new JsonProvider();
            return provider.request(tokenURI, TokenUriMetadata.class);
        } catch (Exception e) {
            throw new NamingServiceException(NSExceptionCode.UnknownError, new NSExceptionParams("m", "getMetadataFromTokenURI"), e);
        }
    }

    private Map<NamingServiceType, NamingService> getServices(IProvider provider) {
        String unsProxyAddress = NetworkConfigLoader.getContractAddress(Network.MAINNET, "ProxyReader");
        String unsl2ProxyAddress = NetworkConfigLoader.getContractAddress(Network.MUMBAI_TESTNET, "ProxyReader");
        Map<NamingServiceType, NamingService> namingServices = new HashMap<>();
        namingServices.put(NamingServiceType.UNS, new UNS(new UNSConfig(
                new NSConfig(Network.MAINNET, ResolutionBuilder.UNS_DEFAULT_URL, unsProxyAddress),
                new NSConfig(Network.MUMBAI_TESTNET, ResolutionBuilder.UNS_L2_DEFAULT_URL, unsl2ProxyAddress))
                , provider));
        namingServices.put(NamingServiceType.ENS, new ENS(new NSConfig(Network.MAINNET, ResolutionBuilder.ENS_DEFAULT_URL, ResolutionBuilder.ENS_DEFAULT_REGISTRY_ADDRESS), provider));
        namingServices.put(NamingServiceType.ZNS, new ZNS(new NSConfig(Network.MAINNET, ResolutionBuilder.ZILLIQA_DEFAULT_URL, ResolutionBuilder.ZNS_DEFAULT_REGISTRY_ADDRESS), provider));
        
        return namingServices;
    }

    private NamingService findService(String domain) throws NamingServiceException {
        String[] split = domain.split("\\.");
        String[] ensTLDs = { "eth", "kred", "luxe", "xyz" };
        if (split.length == 0) {
            throw new NamingServiceException(NSExceptionCode.UnsupportedDomain, new NSExceptionParams("d", domain));
        }
        if (split[split.length - 1].equals("zil")) {
            return services.get(NamingServiceType.ZNS);
        }
        if (Arrays.asList(ensTLDs).contains(split[split.length - 1])) {
            return services.get(NamingServiceType.ENS);
        }
        return services.get(NamingServiceType.UNS);
    }

    private String normalizeDomain(String domain) throws NamingServiceException {
        String normalizedDomain = domain.trim().toLowerCase();
        if (!normalizedDomain.matches("^[.a-z\\d-]+$")) {
            throw new NamingServiceException(NSExceptionCode.InvalidDomain, new NSExceptionParams("d", domain));
        }
        return normalizedDomain;
    }

    // Allows to create a class instance with a private constructor
    public static final class ResolutionBuilderConnector {
        private ResolutionBuilderConnector() {}

        public Resolution buildResolution(Map<NamingServiceType, NamingService> services) {
            return new Resolution(services);
        }
    }
}
