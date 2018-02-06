package com.google.gerrit.server.group;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.audit.group.GroupAuditListener;
import com.google.gerrit.server.notedb.GroupsMigration;

public class Module extends FactoryModule {
  private final GroupsMigration groupsMigration;

  public Module(GroupsMigration groupsMigration) {
    this.groupsMigration = groupsMigration;
  }

  @Override
  protected void configure() {
    if (!groupsMigration.disableGroupReviewDb()) {
      // DbGroupAuditListener is used solely for the ReviewDb audit log. It does not respect
      // ReviewDb wrappers that disable reads. Hence, we don't want to bind it if ReviewDb is
      // disabled.
      DynamicSet.bind(binder(), GroupAuditListener.class).to(DbGroupAuditListener.class);
    }
  }
}
