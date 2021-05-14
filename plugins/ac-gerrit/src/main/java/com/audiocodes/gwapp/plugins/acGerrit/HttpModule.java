package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.httpd.plugins.HttpPluginModule;

public class HttpModule extends HttpPluginModule {
  @Override
  protected void configureServlets() {
    DynamicSet.bind(binder(), WebUiPlugin.class)
        .toInstance(new JavaScriptPlugin("ac-polygerrit.js"));
  }
}
