package com.google.gerrit.server.api;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    bind(GerritApi.class).to(GerritApiImpl.class);
    install(new com.google.gerrit.server.api.changes.Module());
  }
}
