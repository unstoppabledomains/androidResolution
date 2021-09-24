[![Version](https://img.shields.io/maven-central/v/com.unstoppabledomains/resolution)](https://search.maven.org/artifact/com.unstoppabledomains/resolution)
[![CI](https://github.com/unstoppabledomains/resolution-java/workflows/Build%20%26%20test/badge.svg?branch=master)](https://github.com/unstoppabledomains/resolution-java/actions)
[![Version](https://img.shields.io/github/license/unstoppabledomains/resolution-java)](LICENSE)
[![Unstoppable Domains Documentation](https://img.shields.io/badge/docs-unstoppabledomains.com-blue)](https://docs.unstoppabledomains.com/)
[![Get help on Discord](https://img.shields.io/badge/Get%20help%20on-Discord-blueviolet)](https://discord.gg/b6ZVxSZ9Hn)

# Resolution-Java

Resolution-Java is a library for interacting with blockchain domain names. It can be used to retrieve [payment addresses](https://unstoppabledomains.com/features#Add-Crypto-Addresses), IPFS hashes for [decentralized websites](https://unstoppabledomains.com/features#Build-Website), and GunDB usernames for [decentralized chat](https://unstoppabledomains.com/chat).

Resolution-Java is primarily built and maintained by [Unstoppable Domains](https://unstoppabledomains.com/).

Resoultion-Java supports decentralized domains across three zones:

- Unstoppable Name Service (UNS)
  - `.crypto`
  - `.nft`
  - `.blockchain`
  - `.bitcoin`
  - `.coin`
  - `.wallet`
  - `.888`
  - `.dao`
  - `.x`
  - `...`
- Zilliqa Name Service (ZNS)
  - `.zil`
- Ethereum Name Service (ENS)
  - `.eth`
  - `.kred`
  - `.xyz`
  - `.luxe`

# Releases

The most recent release of this library is available on [Maven Central](https://search.maven.org/artifact/com.unstoppabledomains/resolution).

# Usage

### Prerequisites

Java 8+ version is required to use this library.

### Default Ethereum Providers

resolution-java library provides zero-configuration experience by using built-in production-ready Infura endpoint by default.
Default Ethereum provider is free to use without restrictions and rate-limits for UNS resolution. To resolve ENS domains on production it's recommended to change Ethereum provider.

### Custom Ethereum provider configuration

```java
// Default config:

DomainResolution resolution = new Resolution();

// Optionally override default config using builder options:
// providerUrl overwrites chainId by making net_version JSON RPC call to the provider
// in the following example blockchain would be set to the rinkeby testnet
// Note: if a custom configuration is provided for one UNS layer, 
// it should be provided for the other layer too
DomainResolution resolution = Resolution.builder()
                .ensProviderUrl("https://rinkeby.infura.io/v3/e0c0cb9d12c440a29379df066de587e6")
                .ensContractAddress("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
                .unsProviderUrl(UNSLocation.Layer1, "https://rinkeby.infura.io/v3/e0c0cb9d12c440a29379df066de587e6")
                .unsContractAddress(UNSLocation.Layer1, "0x299974AeD8911bcbd2C61262605b89F591a53E83")
                .unsProviderUrl(UNSLocation.Layer2, "https://polygon-mumbai.infura.io/e0c0cb9d12c440a29379df066de587e6")
                .unsContractAddress(UNSLocation.Layer2, NetworkConfigLoader.getContractAddress(Network.MUMBAI_TESTNET, "ProxyReader"))
                .build();

// Custom provider config:

DomainResolution resolution = Resolution.builder()
                .provider(new IProvider() {
                    @Override
                    public JsonObject request(String url, JsonObject body) throws IOException {
                        // TODO Make post request to url with given body
                        // and return JsonObject from the response
                        return null;
                    }
                })
                .build();

// Adding a custom header to the DefaultProvider

DefaultProvider myProvider = DefaultProvider
                .cleanBuild()
                .setHeader("custom-header", "custom-value")
                .setHeader("new-header", "new-value");

DomainResolution resolution = Resolution.builder()
                .provider(myProvider)
                .build();

// All network calls will be made with headers "custom-header" and "new-header" instead of default ones

```

[Live usage examples](samples.md)

### Getting a domain's crypto address

Resolving a domain and getting a currency address.

```java
String addr = resolution.getAddress("brad.crypto", "eth");
assertEquals("0x8aaD44321A86b170879d7A244c1e8d360c99DdA8", addr);

// Get address of token present in multiple chains
String usdtErc20Addr = resolution.getMultiChainAddress("brad.crypto", "USDT", "ERC20");
assertEquals("0x8aaD44321A86b170879d7A244c1e8d360c99DdA8", usdtErc20Addr);

String usdtOmniAddr = resolution.getMultiChainAddress("brad.crypto", "USDT", "OMNI");
assertEquals("1Ap8kmF4ZoPjt6ZYAfCaTKsbncky3F8eTV", usdtOmniAddr);
```

### Getting a domain owner's Ethereum address

Each decentralized domain is owned by someone on the blockchain and held within their wallet. The following command will return the domain owner's Ethereum address.

```java
String owner = resolution.getOwner("brad.crypto");
assertEquals("0x8aad44321a86b170879d7a244c1e8d360c99dda8", owner);
```

You can also get the result in a batch format for UNS:
```java
List<String> domains = Arrays.asList("brad.crypto", "homecakes.crypto");
Map<String, String> owners = mainnetResolution.getBatchOwners(domains);
assertEquals("0x8aad44321a86b170879d7a244c1e8d360c99dda8", owners.get("brad.crypto"));
assertEquals("0xe7474d07fd2fa286e7e0aa23cd107f8379085037", owners.get("homecakes.crypto"));
```
### Getting a domain's IPFS hash

Decentralized websites host their content on decentralized file storage systems such as [IPFS](http://ipfs.io/).
To get the IPFS hash associated with a domain (and therefore its content), you can use the `getIpfsHash` method.

```java
String ipfs = resolution.getIpfsHash("brad.crypto");
assertEquals("Qme54oEzRkgooJbCDr78vzKAWcv6DDEZqRhhDyDtzgrZP6", ipfs);
```

### Getting a domain's record

Retrieve any record of domain. Applications sometimes set custom records for a domain to use within their application. To read these records, use the `getRecord` method.

```java
String record = resolution.getRecord("ryan.crypto", "custom.record.value");
assertEquals("Example custom record value", record);
```

## Errors

If the domain you requested is not registered or doesn't have the record you are looking for, this library will throw a `NamingServiceException` error with one of these codes. We recommend creating customized errors in your app based on the return value of the error.

```java
public enum NSExceptionCode {
  UnsupportedDomain,
  UnregisteredDomain,
  UnknownCurrency,
  RecordNotFound,
  BlockchainIsDown,
  UnknownError,
  IncorrectContractAddress,
  IncorrectMethodName,
  UnspecifiedResolver;
}
```

# Development

## Build & test

> Note: if you don't wish to install Gradle you can use it with wrapper: `./gradlew` instead of `gradle`.

- To run a build with associated tests, use `gradle build`.
- To run a build without running the tests, use `gradle build -x test`.

## Internal network config

Internal [network config](src/main/resources/com/unstoppabledomains/config/network/uns-config.json)
can be updated by running the `gradle pullNetworkConfig` task and committing the updated file.

## Versioning & release process

### On codebase updates

- Contributors should update [CHANGELOG.md](CHANGELOG.md) with the listed changes, and increment the client version in [client.json](src/main/resources/com/unstoppabledomains/client/client.json).

### New packages

- Create a new Github release (using `semver`);
- Wait for a successful Github Workflow publishing;
- Release staging repository by logging into [Nexus Repository Manager](https://oss.sonatype.org/) and
  performing 'close' and then 'release' actions.

As an alternative to a CI release, you can perform a manual publish by following these steps:

- Configure environment variables: `nexusUsername`, `nexusPassword`, `signingKey`, `signingPassword`;
- Run `gradle publish`;
- Login to [Nexus Repository Manager](https://oss.sonatype.org/) and perform 'close' and then
  'release' actions on a staged repository **OR** run `gradle closeAndReleaseRepository`.

# Contributions

Contributions to this library are more than welcome. The easiest way to contribute is through GitHub issues and pull requests.

# Free advertising for integrated apps

Once your app has a working Unstoppable Domains integration, [register it here](https://unstoppabledomains.com/app-submission). Registered apps appear on the Unstoppable Domains [homepage](https://unstoppabledomains.com/) and [Applications](https://unstoppabledomains.com/apps) page — putting your app in front of tens of thousands of potential customers per day.

Also, every week we select a newly-integrated app to feature in the Unstoppable Update newsletter. This newsletter is delivered to straight into the inbox of ~100,000 crypto fanatics — all of whom could be new customers to grow your business.

# Get help

[Join our discord community](https://discord.com/invite/b6ZVxSZ9Hn) and ask questions.
