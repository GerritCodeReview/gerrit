// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.schema;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

/** A version of the database schema. */
public abstract class SchemaVersion {
  /** The current schema version. */
  public static final Class<Schema_84> C = Schema_84.class;

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(SchemaVersion.class).annotatedWith(Current.class).to(C);
    }
  }

  private final Provider<? extends SchemaVersion> prior;
  private final int versionNbr;

  protected SchemaVersion(final Provider<? extends SchemaVersion> prior) {
    this.prior = prior;
    this.versionNbr = guessVersion(getClass());
  }

  public static int guessVersion(Class<?> c) {
    String n = c.getName();
    n = n.substring(n.lastIndexOf('_') + 1);
    while (n.startsWith("0"))
      n = n.substring(1);
    return Integer.parseInt(n);
  }

  protected SchemaVersion(final Provider<? extends SchemaVersion> prior,
      final int versionNbr) {
    this.prior = prior;
    this.versionNbr = versionNbr;
  }

  /** @return the {@link CurrentSchemaVersion#versionNbr} this step targets. */
  public final int getVersionNbr() {
    return versionNbr;
  }

  public final void check(UpdateUI ui, CurrentSchemaVersion curr, ReviewDb db)
      throws OrmException, SQLException {
    if (curr.versionNbr == versionNbr) {
      // Nothing to do, we are at the correct schema.
    } else if (curr.versionNbr > versionNbr) {
      throw new OrmException("Cannot downgrade database schema from version "
          + curr.versionNbr + " to " + versionNbr + ".");
    } else {
      upgradeFrom(ui, curr, db);
    }
  }

  /** Runs check on the prior schema version, and then upgrades. */
  private void upgradeFrom(UpdateUI ui, CurrentSchemaVersion curr, ReviewDb db)
      throws OrmException, SQLException {
    List<SchemaVersion> pending = pending(curr.versionNbr);
    updateSchema(pending, ui, db);
    migrateData(pending, ui, curr, db);

    JdbcSchema s = (JdbcSchema) db;
    JdbcExecutor e = new JdbcExecutor(s);
    try {
      final List<String> pruneList = Lists.newArrayList();
      s.pruneSchema(new StatementExecutor() {
        public void execute(String sql) {
          pruneList.add(sql);
        }
      });

      if (!pruneList.isEmpty()) {
        ui.pruneSchema(e, pruneList);
      }
    } finally {
      e.close();
    }
  }

  private List<SchemaVersion> pending(int curr) {
    List<SchemaVersion> r = Lists.newArrayListWithCapacity(versionNbr - curr);
    for (SchemaVersion v = this; curr < v.getVersionNbr(); v = v.prior.get()) {
      r.add(v);
    }
    Collections.reverse(r);
    return r;
  }

  private void updateSchema(List<SchemaVersion> pending, UpdateUI ui,
      ReviewDb db) throws OrmException, SQLException {
    for (SchemaVersion v : pending) {
      ui.message(String.format("Upgrading schema to %d ...", v.getVersionNbr()));
      v.preUpdateSchema(db);
    }

    JdbcSchema s = (JdbcSchema) db;
    JdbcExecutor e = new JdbcExecutor(s);
    try {
      s.updateSchema(e);
    } finally {
      e.close();
    }
  }

  /** Invoke before updateSchema adds new columns/tables. */
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {
  }

  private void migrateData(List<SchemaVersion> pending, UpdateUI ui,
      CurrentSchemaVersion curr, ReviewDb db) throws OrmException, SQLException {
    for (SchemaVersion v : pending) {
      ui.message(String.format(
          "Migrating data to schema %d ...",
          v.getVersionNbr()));
      v.migrateData(db, ui);
      v.finish(curr, db);
    }
  }

  /**
   * Invoked between updateSchema (adds new columns/tables) and pruneSchema
   * (removes deleted columns/tables).
   */
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
  }

  /** Mark the current schema version. */
  protected void finish(CurrentSchemaVersion curr, ReviewDb db)
      throws OrmException {
    curr.versionNbr = versionNbr;
    db.schemaVersion().update(Collections.singleton(curr));
  }

  /** Rename an existing table. */
  protected void renameTable(ReviewDb db, String from, String to)
      throws OrmException {
    final JdbcSchema s = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(s);
    try {
      s.renameTable(e, from, to);
    } finally {
      e.close();
    }
  }

  /** Rename an existing column. */
  protected void renameColumn(ReviewDb db, String table, String from, String to)
      throws OrmException {
    final JdbcSchema s = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(s);
    try {
      s.renameField(e, table, from, to);
    } finally {
      e.close();
    }
  }

  /** Execute an SQL statement. */
  protected void execute(ReviewDb db, String sql) throws SQLException {
    Statement s = ((JdbcSchema) db).getConnection().createStatement();
    try {
      s.execute(sql);
    } finally {
      s.close();
    }
  }
}
