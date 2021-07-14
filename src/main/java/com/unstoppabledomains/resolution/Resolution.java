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
        services = getServices(UNS_DEFAULT_URL, ENS_DEFAULT_URL, provider);
    }

    /**
     * Resolution object
     *
     * @param blockchainProviderUrl url for the public Ethereum provider
     * @deprecated since 1.8.0
     * <p> Use {@link Resolution#builder()} instead
     */
    @Deprecated
    public Resolution(String blockchainProviderUrl) {
        IProvider provider = new DefaultProvider();
        services = getServices(blockchainProviderUrl, blockchainProviderUrl, provider);
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
        return findService(domain).getRecord(domain, recordKey);
    }

    @Override
    public String getAddress(String domain, String ticker) throws NamingServiceException {
        NamingService service = findService(domain);
        String recordKey = "crypto." + ticker.toUpperCase() + ".address";
        try {
            return service.getRecord(domain, recordKey);
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
        return service.getRecord(domain, recordKey);
    }

    @Override
    public String getUsdt(String domain, TickerVersion version) throws NamingServiceException {
        NamingService service = findService(domain);
        if (service.getType() == NamingServiceType.ENS) {
            throw new NamingServiceException(NSExceptionCode.NotImplemented,
                new NSExceptionParams("d|m", domain, "getMultiChainAddress"));
        }
        String recordKey = "crypto.USDT.version." + version.toString() + ".address";
        return service.getRecord(domain, recordKey);
    }

    @Override
    public String getNamehash(String domain) throws NamingServiceException {
        NamingService service = findService(domain);
        return service.getNamehash(domain);
    }

    @Override
    public String getIpfsHash(String domain) throws NamingServiceException {
        NamingService service = findService(domain);
        String recordKey = "dweb.ipfs.hash";
        return service.getRecord(domain, recordKey);
    }

    @Override
    public String getEmail(String domain) throws NamingServiceException {
        NamingService service = findService(domain);
        String recordKey = "whois.email.value";
        return service.getRecord(domain, recordKey);
    }

    @Override
    public String getOwner(String domain) throws NamingServiceException {
        NamingService service = findService(domain);
        return service.getOwner(domain);
    }

    @Override
    public List<DnsRecord> getDns(String domain, List<DnsRecordsType> types) throws NamingServiceException, DnsException {
        NamingService service = findService(domain);
        return service.getDns(domain, types);
    }

    @Override
    public String addr(String domain, String ticker) throws NamingServiceException {
        return getAddress(domain, ticker);
    }

    @Override
    public String namehash(String domain) throws NamingServiceException {
        return getNamehash(domain);
    }

    @Override
    public String ipfsHash(String domain) throws NamingServiceException {
        return getIpfsHash(domain);
    }

    @Override
    public String email(String domain) throws NamingServiceException {
        return getEmail(domain);
    }

    @Override
    public String owner(String domain) throws NamingServiceException {
        return getOwner(domain);
    }
    
    @Override
    public String tokenURI(String domain) throws NamingServiceException {
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
    public TokenUriMetadata tokenURIMetadata(String domain) throws NamingServiceException {
        String tokenURI = tokenURI(domain);
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

    private Map<NamingServiceType, NamingService> getServices(String UnsProviderUrl, String EnsProviderUrl, IProvider provider) {
        String unsProxyAddress = NetworkConfigLoader.getContractAddress(Network.MAINNET, "ProxyReader");
        return new HashMap<NamingServiceType, NamingService>() {{
            put(NamingServiceType.UNS, new UNS(new NSConfig(Network.MAINNET, UnsProviderUrl, unsProxyAddress), provider));
            put(NamingServiceType.ENS, new ENS(new NSConfig(Network.MAINNET, EnsProviderUrl, ENS_DEFAULT_REGISTRY_ADDRESS), provider));
            put(NamingServiceType.ZNS, new ZNS(new NSConfig(Network.MAINNET, ZILLIQA_DEFAULT_URL, ZNS_DEFAULT_REGISTRY_ADDRESS), provider));
        }};
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

    public static class Builder {
        private static final String INFURA_URL = "https://%s.infura.io/v3/%s";

        private final Map<NamingServiceType, NSConfig> serviceConfigs;
        private IProvider provider;

        private Builder() {
            String unsProxyAddress = NetworkConfigLoader.getContractAddress(Network.MAINNET, "ProxyReader");
            serviceConfigs = new HashMap<NamingServiceType, NSConfig>() {{
                put(NamingServiceType.UNS, new NSConfig(Network.MAINNET, UNS_DEFAULT_URL, unsProxyAddress));
                put(NamingServiceType.ZNS, new NSConfig(Network.MAINNET, ZILLIQA_DEFAULT_URL, ZNS_DEFAULT_REGISTRY_ADDRESS));
                put(NamingServiceType.ENS, new NSConfig(Network.MAINNET, ENS_DEFAULT_URL, ENS_DEFAULT_REGISTRY_ADDRESS));
            }};
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
         * @param nsType      the naming service for which config is applied
         * @param providerUrl blockchain provider URL
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
            Map<NamingServiceType, NamingService> services = new HashMap<NamingServiceType, NamingService>() {{
                put(NamingServiceType.UNS, new UNS(serviceConfigs.get(NamingServiceType.UNS), provider));
                put(NamingServiceType.ZNS, new ZNS(serviceConfigs.get(NamingServiceType.ZNS), provider));
                put(NamingServiceType.ENS, new ENS(serviceConfigs.get(NamingServiceType.ENS), provider));
            }};
            return new Resolution(services);
        }

        private void setProvider(IProvider provider) {
            this.provider = provider;
        }

        /**
         * Makes a call via provider to the blockchainProviderUrl and returns the networkId
         * @param blockchainProviderUrl
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
                e.printStackTrace();
                return null;
            }
        }
    }
}
