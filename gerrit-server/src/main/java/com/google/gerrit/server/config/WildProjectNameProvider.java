package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

class WildProjectNameProvider implements Provider<Project.NameKey> {
  /** Project.Id meaning "any and all projects on this server". */
  static final Project.Id WILD_PROJECT_ID = new Project.Id(0);

  private final SchemaFactory<ReviewDb> schema;

  @Inject
  WildProjectNameProvider(final SchemaFactory<ReviewDb> schema,
  /*
   * Unused, but we need to force it to load before we do, otherwise we risk
   * reading an empty database without the wild project being in the database.
   * Asking for it should ensures Guice loads it first.
   */
  final SystemConfig config) {
    this.schema = schema;
  }

  public Project.NameKey get() {
    try {
      final ReviewDb db = schema.open();
      try {
        final Project p = db.projects().get(WILD_PROJECT_ID);
        if (p == null) {
          throw new ProvisionException("No project " + WILD_PROJECT_ID);
        }
        return p.getNameKey();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot load " + WILD_PROJECT_ID, e);
    }
  }
}
