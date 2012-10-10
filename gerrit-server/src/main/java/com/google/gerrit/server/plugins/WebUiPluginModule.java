package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class WebUiPluginModule extends AbstractModule {

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), RegisteredWebUiPlugin.class);
  }

}
