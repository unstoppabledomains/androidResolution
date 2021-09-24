package com.unstoppabledomains.resolution.naming.service.uns;

import java.util.concurrent.Callable;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResolutionMethods<T> {
  private Callable<T> l1Func;
  private Callable<T> l2Func;
}
