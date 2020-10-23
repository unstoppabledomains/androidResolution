package com.unstoppabledomains.resolution;

import com.unstoppabledomains.exceptions.NamingServiceException;

abstract interface NamingService {
    abstract Boolean isSupported(String domain);
    abstract String addr(String domain, String ticker) throws NamingServiceException;
    abstract String ipfsHash(String domain) throws NamingServiceException;
    abstract String email(String domain) throws NamingServiceException;
    abstract String owner(String domain) throws NamingServiceException;
    abstract String namehash(String domain) throws NamingServiceException;
}