package com.unstoppabledomains.exceptions.ns;

import java.util.regex.Pattern;

public class NSExceptionParams {
  public static final NSExceptionParams EMPTY_PARAMS = new NSExceptionParams(" ", " ");

  public String domain;
  public String namingService;
  public String coinTicker;
  public String contractAddress;
  public String methodName;
  public String record;
  public String threadLimit;
  public String layer;

  public NSExceptionParams(String format, String ...args) {
    Pattern pattern = Pattern.compile("\\|");
    String[] options = pattern.split(format);
    int index = 0;
    for (String option: options) {
      parseOption(option, args[index++]);
    }
  }

  private void parseOption(String option, String value) {
    switch(option) {
      case "d": {
        domain = value;
        return ;
      }
      case "n": {
        namingService = value;
        return ;
      }
      case "c": {
        coinTicker = value;
        return;
      }
      case "a": {
        contractAddress = value;
        return;
      }
      case "m": {
        methodName = value;
        return;
      }
      case "r": {
        record = value;
        break ;
      }
      case "l": {
        threadLimit = value;
        break ;
      }
      case "u": {
        layer = value;
        break ;
      }
      default: {
        break ;
      }
    }
  }
}