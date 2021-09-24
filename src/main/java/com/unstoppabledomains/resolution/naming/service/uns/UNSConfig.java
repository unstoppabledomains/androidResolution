package com.unstoppabledomains.resolution.naming.service.uns;
import com.unstoppabledomains.resolution.naming.service.NSConfig;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UNSConfig {
  private NSConfig layer1;
  private NSConfig layer2;
}

