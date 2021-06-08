package com.google.gerrit.httpd;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;

public class HttpdModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(GitReferenceUpdatedListener.class)
        .annotatedWith(Exports.named(GitReferenceUpdatedTracker.class.getSimpleName()))
        .to(GitReferenceUpdatedTracker.class);
  }
}
