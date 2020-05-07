package com.google.gerrit.server.config;

import static com.google.inject.Scopes.SINGLETON;

import com.google.inject.AbstractModule;

public class GerritInstanceIdModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(String.class)
        .annotatedWith(GerritInstanceId.class)
        .toProvider(GerritInstanceIdProvider.class)
        .in(SINGLETON);
  }
}
