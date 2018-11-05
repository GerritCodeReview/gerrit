package com.google.gerrit.server.audit;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class AuditEventDispatcherModule extends AbstractModule {

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), AuditListener.class);
    bind(AuditEventDispatcher.class).to(AuditEventDispatcherImpl.class);
  }
}
