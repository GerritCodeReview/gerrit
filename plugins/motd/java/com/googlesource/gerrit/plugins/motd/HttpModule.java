package com.googlesource.gerrit.plugins.motd;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.inject.AbstractModule;

public class HttpModule extends AbstractModule {
  @Override
  protected void configure() {
    install(
        new RestApiModule() {
          @Override
          protected void configure() {
            DynamicSet.bind(binder(), WebUiPlugin.class)
                .toInstance(new JavaScriptPlugin("gr-motd.js"));
          }
        });
  }
}
