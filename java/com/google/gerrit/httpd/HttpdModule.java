package com.google.gerrit.httpd;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.GitReferencesUpdatedListener;

public class HttpdModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(GitReferencesUpdatedListener.class)
        .annotatedWith(Exports.named(GitReferencesUpdatedTracker.class.getSimpleName()))
        .to(GitReferencesUpdatedTracker.class);
  }
}
