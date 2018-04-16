package com.google.gerrit.server.config;

import com.google.common.collect.ImmutableSet;

public class GerritConfigListenerHelper {
  public static GerritConfigListener alwaysAccept(ConfigKey... keys) {
    return e -> e.accept(ImmutableSet.copyOf(keys));
  }
}
