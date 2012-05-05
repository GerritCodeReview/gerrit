package com.google.gerrit.server.git.validators;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class ValidationModule extends AbstractModule {

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), ValidationListener.class);
  }

}
