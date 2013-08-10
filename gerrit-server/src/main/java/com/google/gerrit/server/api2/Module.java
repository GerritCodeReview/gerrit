package com.google.gerrit.server.api2;

import com.google.gerrit.extensions.api2.GerritApi;
import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    bind(GerritApi.class).to(GerritApiImpl.class);
    install(new com.google.gerrit.server.api2.changes.Module());
  }
}
