package com.unstoppabledomains.resolution;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import com.unstoppabledomains.resolution.naming.service.UNS;
import com.unstoppabledomains.resolution.naming.service.NSConfig;
import com.unstoppabledomains.resolution.naming.service.NamingService;
import com.unstoppabledomains.resolution.naming.service.NamingServiceType;
import com.unstoppabledomains.resolution.naming.service.ZNS;
import com.unstoppabledomains.util.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Resolution implements DomainResolution {
    private static final String ENS_DEFAULT_URL = "https://mainnet.infura.io/v3/d423cf2499584d7fbe171e33b42cfbee";
    private static final String UNS_DEFAULT_URL = "https://mainnet.infura.io/v3/e0c0cb9d12c440a29379df066de587e6";
    private static final String ZILLIQA_DEFAULT_URL = "https://api.zilliqa.com";

    private static final String ENS_DEFAULT_REGISTRY_ADDRESS = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e";
    private static final String ZNS_DEFAULT_REGISTRY_ADDRESS = "0x9611c53BE6d1b32058b2747bdeCECed7e1216793";

    private Map<NamingServiceType, NamingService> services;

    /**
     * Use {@link Builder} methods to override default configs
     * or get Resolution object with default settings
     * by calling {@link Builder#build()}
     *
     * @return builder object
     */
    public static Builder builder() {
        return new Builder();
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
        Map<NamingServiceType, NamingService> namingServices = new HashMap<>();
        namingServices.put(NamingServiceType.UNS, new UNS(new NSConfig(Network.MAINNET, Resolution.UNS_DEFAULT_URL, unsProxyAddress), provider));
        namingServices.put(NamingServiceType.ENS, new ENS(new NSConfig(Network.MAINNET, Resolution.ENS_DEFAULT_URL, ENS_DEFAULT_REGISTRY_ADDRESS), provider));
        namingServices.put(NamingServiceType.ZNS, new ZNS(new NSConfig(Network.MAINNET, ZILLIQA_DEFAULT_URL, ZNS_DEFAULT_REGISTRY_ADDRESS), provider));

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

    public static class Builder {
        private static final String INFURA_URL = "https://%s.infura.io/v3/%s";

        private final Map<NamingServiceType, NSConfig> serviceConfigs;
        private IProvider provider;

        private Builder() {
            // todo add example with testnet config
            // todo require custom contract address if network is not mainnet (throw an exception)
            // todo If network hasn't changed - don't require contract address
            String unsProxyAddress = NetworkConfigLoader.getContractAddress(Network.MAINNET, "ProxyReader");
        
            serviceConfigs = new HashMap<>(); 
            serviceConfigs.put(NamingServiceType.UNS, new NSConfig(Network.MAINNET, UNS_DEFAULT_URL, unsProxyAddress));
            serviceConfigs.put(NamingServiceType.ZNS, new NSConfig(Network.MAINNET, ZILLIQA_DEFAULT_URL, ZNS_DEFAULT_REGISTRY_ADDRESS));
            serviceConfigs.put(NamingServiceType.ENS, new NSConfig(Network.MAINNET, ENS_DEFAULT_URL, ENS_DEFAULT_REGISTRY_ADDRESS));
            
            provider = new DefaultProvider();
        }

        /**
         * @param nsType  the naming service for which config is applied
         * @param chainId blockchain network ID
         * @return builder object to allow chaining
         */
        public Builder chainId(NamingServiceType nsType, Network chainId) {
            NSConfig nsConfig = serviceConfigs.get(nsType);
            nsConfig.setChainId(chainId);
            return this;
        }

        /**
         * @param nsType      the naming service for which config is applied
         * @param providerUrl blockchain provider URL
         * @return builder object to allow chaining
         */
        public Builder providerUrl(NamingServiceType nsType, String providerUrl) {
            NSConfig nsConfig = serviceConfigs.get(nsType);
            Network chainId = getNetworkId(providerUrl);
            if (chainId == null) {
                chainId = nsConfig.getChainId();
            }
            nsConfig.setBlockchainProviderUrl(providerUrl);
            nsConfig.setChainId(chainId);
            return this;
        }

        /**
         * @param nsType            the naming service for which config is applied
         * @param contractAddress   address of `ProxyReader` contract for UNS | address of `Registry` contract for ZNS or ENS
         * @return builder object to allow chaining
         */
        public Builder contractAddress(NamingServiceType nsType, String contractAddress) {
            NSConfig nsConfig = serviceConfigs.get(nsType);
            nsConfig.setContractAddress(contractAddress);
            return this;
        }

        /**
         * Configuration for <a href="https://infura.io">infura.io</a>
         * blockchain provider with previously set network ID using
         * {@link Builder#chainId(NamingServiceType, Network)} or default one
         * ({@link Network#MAINNET})
         *
         * @param nsType    the naming service for which config is applied
         * @param projectId Infura project ID
         * @return builder object to allow chaining
         */
        public Builder infura(NamingServiceType nsType, String projectId) {
            NSConfig nsConfig = serviceConfigs.get(nsType);
            final Network network = nsConfig.getChainId();
            return infura(nsType, network, projectId);
        }

        /**
         * Configuration for <a href="https://infura.io">infura</a> blockchain provider
         *
         * @param nsType    the naming service for which config is applied
         * @param chainId   blockchain network ID
         * @param projectId Infura project ID
         * @return builder object to allow chaining
         */
        public Builder infura(NamingServiceType nsType, Network chainId, String projectId) {
            NSConfig nsConfig = serviceConfigs.get(nsType);
            nsConfig.setChainId(chainId);

            String infuraUrl = String.format(INFURA_URL, chainId.getName(), projectId);
            nsConfig.setBlockchainProviderUrl(infuraUrl);

            return this;
        }

        /**
         * Configuration for provider interface, use if needed to take control over network calls
         * @param provider must implement IProvider interface
         * @return builder object to allow chaining
         */
        public Builder provider(IProvider provider) {
            setProvider(provider);
            return this;
        } 

        /**
         * Call directly to get default configs or override them with {@link Builder} methods
         *
         * @return resolution object
         */
        public Resolution build() {
            Map<NamingServiceType, NamingService> services = new HashMap<>();
            services.put(NamingServiceType.UNS, new UNS(serviceConfigs.get(NamingServiceType.UNS), provider));
            services.put(NamingServiceType.ZNS, new ZNS(serviceConfigs.get(NamingServiceType.ZNS), provider));
            services.put(NamingServiceType.ENS, new ENS(serviceConfigs.get(NamingServiceType.ENS), provider));
            
            return new Resolution(services);
        }

        private void setProvider(IProvider provider) {
            this.provider = provider;
        }

        /**
         * Makes a call via provider to the blockchainProviderUrl and returns the networkId
         * @param blockchainProviderUrl RPC endpoint url
         * @return Network object or null if couldn't retrive the network
         */
        private Network getNetworkId(String blockchainProviderUrl) {
            JsonObject body = new JsonObject();
            body.addProperty("jsonrpc", "2.0");
            body.addProperty("method", "net_version");
            body.add("params", new JsonArray());
            body.addProperty("id", 67);
            try {
                JsonObject response = provider.request(blockchainProviderUrl, body);
                return Network.getNetwork(response.get("result").getAsInt());
            } catch(Exception e) {
                return null;
            }
        }
    }
}
