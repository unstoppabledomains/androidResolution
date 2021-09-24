package com.unstoppabledomains.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.unstoppabledomains.config.network.model.Network;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

public class BuilderNSConfigTest {

    @Test
    public void ChecksDefaultConfig() {
        BuilderNSConfig config = new BuilderNSConfig(Network.MAINNET, "default provider url", "default contract address");

        assertEquals(true, config.isConfigured());
        assertEquals(true, config.isDefault());
    }

    @Test
    public void ChecksFullyConfigured() {
        BuilderNSConfig config = new BuilderNSConfig(Network.MAINNET, "default provider url", "default contract address");

        config.setChainId(Network.RINKEBY);
        config.setBlockchainProviderUrl("custom provider");
        config.setContractAddress("custom contract");

        assertEquals(true, config.isConfigured());
        assertEquals(false, config.isDefault());
    }

    private interface ConfigModifierInterface {
        void use(BuilderNSConfig config);
    }

    private class TestData {
        private String name;
        private String message;
        private ConfigModifierInterface modifier;
        public TestData(String name, String message, ConfigModifierInterface modifier) {
          this.name = name;
          this.message = message;
          this.modifier = modifier;
        }
    }

    List<TestData> notFullyConfiguredCases = new ArrayList<>(Arrays.asList(
        new TestData("set chainId only", "Provider URL is not set; Contract address is not set", (c) -> c.setChainId(Network.RINKEBY)),
        new TestData("set provider url only", "Chain ID is not set; Contract address is not set", (c) -> c.setBlockchainProviderUrl("custom provider")),
        new TestData("set contract only", "Chain ID is not set; Provider URL is not set", (c) -> c.setContractAddress("custom contract")),
        new TestData("set chainId and provider url", "Contract address is not set", (c) -> {c.setChainId(Network.RINKEBY); c.setBlockchainProviderUrl("custom provider");}),
        new TestData("set chainId and contract", "Provider URL is not set", (c) -> {c.setChainId(Network.RINKEBY); c.setContractAddress("custom contract");}),
        new TestData("set provider url and contract", "Chain ID is not set", (c) -> {c.setBlockchainProviderUrl("custom provider"); c.setContractAddress("custom contract");})
    ));

    @TestFactory
    public Iterator<DynamicTest> ChecksPartiallyConfigured() {
        return notFullyConfiguredCases.stream().map(test -> DynamicTest.dynamicTest("Test partial configuration: " + test.name, () -> {
            BuilderNSConfig config = new BuilderNSConfig(Network.MAINNET, "default provider url", "default contract address");
    
            test.modifier.use(config);
    
            assertEquals(false, config.isConfigured());
            assertEquals(false, config.isDefault());
            assertEquals(test.message, config.getMisconfiguredMessage());
        })).iterator();
    }
}
