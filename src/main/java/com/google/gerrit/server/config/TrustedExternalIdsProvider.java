package com.google.gerrit.server.config;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.reviewdb.TrustedExternalId;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class TrustedExternalIdsProvider implements Provider<Collection<TrustedExternalId>> {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  TrustedExternalIdsProvider(final SchemaFactory<ReviewDb> schema,
  /*
   * Unused, but we need to force it to load before we do, otherwise we risk
   * reading an empty database without the wild project being in the database.
   * Asking for it should ensures Guice loads it first.
   */
  final SystemConfig config) {
    this.schema = schema;
  }

  public Collection<TrustedExternalId> get() {
    final List<TrustedExternalId> l;
    try {
      final ReviewDb db = schema.open();
      try {
        l = db.trustedExternalIds().all().toList();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot load TrustedExternalIds", e);
    }
    return Collections.unmodifiableList(l);
  }
}
