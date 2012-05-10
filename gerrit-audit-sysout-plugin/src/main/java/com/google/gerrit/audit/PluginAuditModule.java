package com.google.gerrit.audit;

import com.google.gerrit.audit.AuditListener;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.internal.UniqueAnnotations;


/**
 * Hello world!
 * 
 */
public class PluginAuditModule extends AbstractModule {

  @Override
  protected void configure() {

    DynamicSet.bind(binder(), AuditListener.class).to(SystemOutAudit.class);

    // bind(AuditListener.class).annotatedWith(UniqueAnnotations.create()).to(
    // SystemOutAudit.class);
  }
}
