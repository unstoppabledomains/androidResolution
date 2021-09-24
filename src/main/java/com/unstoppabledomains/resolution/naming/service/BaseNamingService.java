package com.unstoppabledomains.resolution.naming.service;

import com.unstoppabledomains.config.network.model.Network;
import com.unstoppabledomains.resolution.contracts.interfaces.IProvider;

public abstract class BaseNamingService implements NamingService {
    protected Network chainId;
    protected String blockchainProviderUrl;
    protected String contractAddress;
    protected IProvider provider;

    protected BaseNamingService(NSConfig nsConfig, IProvider provider) {
        this.chainId = nsConfig.getChainId();
        this.blockchainProviderUrl = nsConfig.getBlockchainProviderUrl();
        this.contractAddress = nsConfig.getContractAddress();
        this.provider = provider;
    }

    public Network getNetwork() {
        return chainId;
    }

    public String getProviderUrl() {
        return blockchainProviderUrl;
    }

    public String getContractAddress() {
        return contractAddress;
    }
}
