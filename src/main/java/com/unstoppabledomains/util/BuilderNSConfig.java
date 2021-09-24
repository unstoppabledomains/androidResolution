package com.unstoppabledomains.util;

import java.util.ArrayList;
import java.util.List;

import com.unstoppabledomains.config.network.model.Network;
import com.unstoppabledomains.resolution.naming.service.NSConfig;

public class BuilderNSConfig extends NSConfig {
    private int state = BuilderNSConfig.DEFAULT;
    private static final int DEFAULT = 0;
    private static final int CHAIN_ID_SET = 1;
    private static final int PROVIDER_URL_SET = 2;
    private static final int CONTRACT_SET = 4;
    private static final int COMPLETE = CHAIN_ID_SET | PROVIDER_URL_SET | CONTRACT_SET;

    public BuilderNSConfig(Network chainId, String blockchainProviderUrl, String contractAddress) {
        super(chainId, blockchainProviderUrl, contractAddress);
    }

    @Override
    public void setChainId(Network chainId) {
        super.setChainId(chainId);
        this.state |= BuilderNSConfig.CHAIN_ID_SET;
    }

    @Override
    public void setBlockchainProviderUrl(String provider) {
        super.setBlockchainProviderUrl(provider);
        this.state |= BuilderNSConfig.PROVIDER_URL_SET;
    }

    @Override
    public void setContractAddress(String contract) {
        super.setContractAddress(contract);
        this.state |= BuilderNSConfig.CONTRACT_SET;
    }

    public boolean isConfigured() {
        return state == DEFAULT || state == COMPLETE;
    }

    public boolean isDefault() {
        return state == DEFAULT;
    }

    public String getMisconfiguredMessage() {
        List<String> messages = new ArrayList<>();
        if ((state & CHAIN_ID_SET) == 0) {
            messages.add("Chain ID is not set");
        }
        if ((state & PROVIDER_URL_SET) == 0) {
            messages.add("Provider URL is not set");
        }
        if ((state & CONTRACT_SET) == 0) {
            messages.add("Contract address is not set");
        }
        return String.join("; ", messages);
    }
}