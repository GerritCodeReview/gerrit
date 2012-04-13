package com.google.gerrit.audit;

import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.RemovePluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.AbstractModule;
import com.google.inject.internal.UniqueAnnotations;

public class AuditModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(AuditService.class);

    bind(StartPluginListener.class).annotatedWith(UniqueAnnotations.create())
        .to(AuditService.class);

    bind(ReloadPluginListener.class).annotatedWith(UniqueAnnotations.create())
        .to(AuditService.class);

    bind(RemovePluginListener.class).annotatedWith(UniqueAnnotations.create())
        .to(AuditService.class);
  }

}
