package com.google.gerrit.server.account;

import com.google.inject.AbstractModule;

public class AccountModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AuthRequestFactory.class);
  }
}
