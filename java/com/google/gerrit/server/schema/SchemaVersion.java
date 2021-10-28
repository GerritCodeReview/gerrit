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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.UsedAt;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** A version of the database schema. */
public abstract class SchemaVersion {
  /** The current schema version. */
  public static final Class<Schema_170> C = Schema_170.class;

  public static int getBinaryVersion() {
    return guessVersion(C);
  }

  private final Provider<? extends SchemaVersion> prior;
  private final int versionNbr;
  private static SortedSet<Project.NameKey> projects;

  protected SchemaVersion(Provider<? extends SchemaVersion> prior) {
    this.prior = prior;
    this.versionNbr = guessVersion(getClass());
  }

  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public static int guessVersion(Class<?> c) {
    String n = c.getName();
    n = n.substring(n.lastIndexOf('_') + 1);
    while (n.startsWith("0")) {
      n = n.substring(1);
    }
    return Integer.parseInt(n);
  }

  /** @return the {@link CurrentSchemaVersion#versionNbr} this step targets. */
  public final int getVersionNbr() {
    return versionNbr;
  }

  @VisibleForTesting
  public final SchemaVersion getPrior() {
    return prior.get();
  }

  public final void check(UpdateUI ui, CurrentSchemaVersion curr, SchemaFactory<ReviewDb> schema)
      throws OrmException, SQLException {
    if (curr.versionNbr == versionNbr) {
      // Nothing to do, we are at the correct schema.
    } else if (curr.versionNbr > versionNbr) {
      throw new OrmException(
          "Cannot downgrade database schema from version "
              + curr.versionNbr
              + " to "
              + versionNbr
              + ".");
    } else {
      upgradeFrom(ui, curr, schema);
    }
  }

  /** Runs check on the prior schema version, and then upgrades. */
  private void upgradeFrom(UpdateUI ui, CurrentSchemaVersion curr, SchemaFactory<ReviewDb> schema)
      throws OrmException, SQLException {
    List<SchemaVersion> pending = pending(curr.versionNbr);

    try (ReviewDb db = ReviewDbUtil.unwrapDb(schema.open())) {
      updateSchema(pending, ui, db);
    }

    migrateData(pending, ui, curr, schema);

    try (ReviewDb db = ReviewDbUtil.unwrapDb(schema.open())) {
      JdbcSchema s = (JdbcSchema) db;
      List<String> pruneList = new ArrayList<>();
      s.pruneSchema(
          new StatementExecutor() {
            @Override
            public void execute(String sql) {
              pruneList.add(sql);
            }

            @Override
            public void close() {
              // Do nothing.
            }
          });

      try (JdbcExecutor e = new JdbcExecutor(s)) {
        if (!pruneList.isEmpty()) {
          ui.pruneSchema(e, pruneList);
        }
      }
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

  private void updateSchema(List<SchemaVersion> pending, UpdateUI ui, ReviewDb db)
      throws OrmException, SQLException {
    for (SchemaVersion v : pending) {
      ui.message(String.format("Upgrading schema to %d ...", v.getVersionNbr()));
      v.preUpdateSchema(db);
    }

    JdbcSchema s = (JdbcSchema) db;
    try (JdbcExecutor e = new JdbcExecutor(s)) {
      s.updateSchema(e);
    }
  }

  /**
   * Invoked before updateSchema adds new columns/tables.
   *
   * @param db open database handle.
   * @throws OrmException if a Gerrit-specific exception occurred.
   * @throws SQLException if an underlying SQL exception occurred.
   */
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {}

  private void migrateData(
      List<SchemaVersion> pending,
      UpdateUI ui,
      CurrentSchemaVersion curr,
      SchemaFactory<ReviewDb> schema)
      throws OrmException, SQLException {
    for (SchemaVersion v : pending) {
      Stopwatch sw = Stopwatch.createStarted();
      ui.message(String.format("Migrating data to schema %d ...", v.getVersionNbr()));
      try (ReviewDb db = ReviewDbUtil.unwrapDb(schema.open())) {
        v.migrateData(db, ui);
      }
      try (ReviewDb db = ReviewDbUtil.unwrapDb(schema.open())) {
        v.finish(curr, db);
      }
      ui.message(String.format("\t> Done (%.3f s)", sw.elapsed(TimeUnit.MILLISECONDS) / 1000d));
    }
  }

  /**
   * Invoked between updateSchema (adds new columns/tables) and pruneSchema (removes deleted
   * columns/tables).
   *
   * @param db open database handle.
   * @param ui interface for interacting with the user.
   * @throws OrmException if a Gerrit-specific exception occurred.
   * @throws SQLException if an underlying SQL exception occurred.
   */
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {}

  /** Mark the current schema version. */
  protected void finish(CurrentSchemaVersion curr, ReviewDb db) throws OrmException {
    curr.versionNbr = versionNbr;
    db.schemaVersion().update(Collections.singleton(curr));
  }

  /** Rename an existing table. */
  protected static void renameTable(ReviewDb db, String from, String to) throws OrmException {
    JdbcSchema s = (JdbcSchema) db;
    try (JdbcExecutor e = new JdbcExecutor(s)) {
      s.renameTable(e, from, to);
    }
  }

  /** Rename an existing column. */
  protected static void renameColumn(ReviewDb db, String table, String from, String to)
      throws OrmException {
    JdbcSchema s = (JdbcSchema) db;
    try (JdbcExecutor e = new JdbcExecutor(s)) {
      s.renameColumn(e, table, from, to);
    }
  }

  /** Execute an SQL statement. */
  protected static void execute(ReviewDb db, String sql) throws SQLException {
    try (Statement s = newStatement(db)) {
      s.execute(sql);
    }
  }

  /** Open a new single statement. */
  protected static Statement newStatement(ReviewDb db) throws SQLException {
    return ((JdbcSchema) db).getConnection().createStatement();
  }

  /** Open a new prepared statement. */
  protected static PreparedStatement prepareStatement(ReviewDb db, String sql) throws SQLException {
    return ((JdbcSchema) db).getConnection().prepareStatement(sql);
  }

  /** Open a new statement executor. */
  protected static JdbcExecutor newExecutor(ReviewDb db) throws OrmException {
    return new JdbcExecutor(((JdbcSchema) db).getConnection());
  }

  protected static SortedSet<Project.NameKey> getSortedProjectsFromCache(
      GitRepositoryManager repoManager) {
    // TODO: Use Injection/Supplier pattern
    if (projects == null) {
      projects = repoManager.list();
    }
    return projects;
  }

  protected int getThreads() {
    return Runtime.getRuntime().availableProcessors();
  }

  protected ExecutorService createExecutor(UpdateUI ui) {
    int threads = getThreads();
    ui.message(String.format("... using %d threads ...", threads));
    return Executors.newFixedThreadPool(threads);
  }

  @FunctionalInterface
  protected interface ThrowingFunction<I, T> {
    default T accept(I input) {
      try {
        return acceptWithThrow(input);
      } catch (OrmException | IOException e) {
        throw new RuntimeException(e);
      }
    }

    T acceptWithThrow(I input) throws OrmException, IOException;
  }

  protected Collection<?> runParallelTasks(
      ExecutorService executor, Collection<?> lst, ThrowingFunction task, UpdateUI ui) {
    Collection<Object> returnSet = new HashSet<>();
    Set<Future> futures =
        lst.stream()
            .map(each -> executor.submit(() -> task.accept(each)))
            .collect(Collectors.toSet());
    for (Future each : futures) {
      try {
        Object rtn = each.get();
        if (Objects.nonNull(rtn)) {
          returnSet.add(rtn);
        }
      } catch (InterruptedException e) {
        ui.message(
            String.format(
                "Migration step was interrupted. Only %d of %d tasks done.",
                countDone(futures), lst.size()));
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        ui.message(e.getCause().getMessage());
      }
    }
    return returnSet;
  }

  protected CommitBuilder buildCommit(PersonIdent ident, ObjectId tree, String message) {
    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(tree);
    cb.setCommitter(ident);
    cb.setAuthor(ident);
    cb.setMessage(message);
    return cb;
  }

  protected static ObjectId emptyTree(ObjectInserter oi) throws IOException {
    return oi.insert(Constants.OBJ_TREE, new byte[] {});
  }

  protected static ObjectInserter getPackInserterFirst(Repository repo) {
    if (repo instanceof FileRepository) {
      return ((FileRepository) repo).getObjectDatabase().newPackInserter();
    }
    return repo.getObjectDatabase().newInserter();
  }

  private static long countDone(Collection<Future> futures) {
    return futures.stream().filter(Future::isDone).count();
  }
}
