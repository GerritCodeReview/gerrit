// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Make sure that for every account a user branch exists that has an initial empty commit with the
 * registration date as commit time.
 *
 * <p>For accounts that don't have a user branch yet the user branch is created with an initial
 * empty commit that has the registration date as commit time.
 *
 * <p>For accounts that already have a user branch the user branch is rewritten and an initial empty
 * commit with the registration date as commit time is inserted (if such a commit doesn't exist
 * yet).
 */
public class Schema_146 extends SchemaVersion {
  private static final String CREATE_ACCOUNT_MSG = "Create Account";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverIdent;
  private AtomicInteger migratedAccounts = new AtomicInteger();
  private AtomicInteger packs = new AtomicInteger();
  private Stopwatch sw = Stopwatch.createStarted();
  ReentrantLock gcLock = new ReentrantLock();
  private int size;

  @Inject
  Schema_146(
      Provider<Schema_145> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverIdent) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    ui.message("Migrating accounts");
    Set<Entry<Account.Id, Timestamp>> accounts = scanAccounts(db, ui).entrySet();
    Set<List<Entry<Account.Id, Timestamp>>> batches =
        Sets.newHashSet(Iterables.partition(accounts, 500));
    ExecutorService pool = createExecutor(ui);
    try {
      batches.stream()
          .forEach(
              batch -> {
                @SuppressWarnings("unused")
                Future<?> unused = pool.submit(() -> processBatch(batch, ui));
              });
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    ui.message(
        String.format(
            "... (%.3f s) Migrated all %d accounts to schema 146",
            elapsed(), migratedAccounts.get()));
  }

  @Override
  protected int getThreads() {
    try {
      return Integer.parseInt(System.getProperty("threadcount"));
    } catch (NumberFormatException e) {
      return super.getThreads();
    }
  }

  private void processBatch(List<Entry<Account.Id, Timestamp>> batch, UpdateUI ui) {
    try (Repository repo = repoManager.openRepository(allUsersName);
        ObjectInserter inserter = getPackInserterFirst(repo);
        ObjectReader reader = inserter.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RefDatabase refDb = repo.getRefDatabase();
      BatchRefUpdate bru =
          refDb instanceof RefDirectory
              ? ((RefDirectory) refDb).newBatchUpdate(false)
              : refDb.newBatchUpdate();
      bru.setAtomic(refDb instanceof RefDirectory);
      bru.setAllowNonFastForwards(true);
      bru.setRefLogIdent(serverIdent);
      bru.setRefLogMessage(getClass().getSimpleName(), true);
      ObjectId emptyTree = emptyTree(inserter);

      for (Map.Entry<Account.Id, Timestamp> e : batch) {
        Account.Id accountId = e.getKey();
        Timestamp registeredOn = e.getValue();
        String refName = RefNames.refsUsers(accountId);
        Ref ref = repo.exactRef(refName);
        if (ref != null) {
          rewriteUserBranch(rw, inserter, emptyTree, ref, registeredOn, bru);
        } else {
          createUserBranch(inserter, emptyTree, refName, registeredOn, bru);
        }
      }
      if (!bru.getCommands().isEmpty()) {
        inserter.flush();
        bru.execute(rw, NullProgressMonitor.INSTANCE);
        if (inserter instanceof PackInserter && packs.incrementAndGet() % 50 == 0) {
          runGcInBackground(repoManager, allUsersName, ui);
        }
      }
      showProgress(ui, migratedAccounts.addAndGet(batch.size()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private double elapsed() {
    return sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
  }

  private void showProgress(UpdateUI ui, int count) {
    if (count % 100 == 0) {
      ui.message(
          String.format(
              "... (%.3f s) migrated %d%% (%d/%d) accounts",
              elapsed(), Math.round(100.0 * count / size), count, size));
    }
  }

  private void rewriteUserBranch(
      RevWalk rw,
      ObjectInserter oi,
      ObjectId emptyTree,
      Ref ref,
      Timestamp registeredOn,
      BatchRefUpdate bru)
      throws IOException {

    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);
    rw.markStart(rw.parseCommit(ref.getObjectId()));

    RevCommit c;
    ObjectId current = null;
    while ((c = rw.next()) != null) {
      if (isInitialEmptyCommit(emptyTree, c)) {
        return;
      }

      if (current == null) {
        current = createInitialEmptyCommit(oi, emptyTree, registeredOn);
      }

      CommitBuilder cb = new CommitBuilder();
      cb.setParentId(current);
      cb.setTreeId(c.getTree());
      cb.setAuthor(c.getAuthorIdent());
      cb.setCommitter(c.getCommitterIdent());
      cb.setMessage(c.getFullMessage());
      cb.setEncoding(c.getEncoding());
      current = oi.insert(cb);
    }

    bru.addCommand(
        new ReceiveCommand(
            ref.getObjectId(), current, ref.getName(), ReceiveCommand.Type.UPDATE_NONFASTFORWARD));
  }

  public void createUserBranch(
      ObjectInserter oi,
      ObjectId emptyTree,
      String refName,
      Timestamp registeredOn,
      BatchRefUpdate bru)
      throws IOException {
    ObjectId id = createInitialEmptyCommit(oi, emptyTree, registeredOn);
    bru.addCommand(new ReceiveCommand(ObjectId.zeroId(), id, refName));
  }

  private ObjectId createInitialEmptyCommit(
      ObjectInserter oi, ObjectId emptyTree, Timestamp registrationDate) throws IOException {
    return oi.insert(
        buildCommit(new PersonIdent(serverIdent, registrationDate), emptyTree, CREATE_ACCOUNT_MSG));
  }

  private boolean isInitialEmptyCommit(ObjectId emptyTree, RevCommit c) {
    return c.getParentCount() == 0
        && c.getTree().equals(emptyTree)
        && c.getShortMessage().equals(CREATE_ACCOUNT_MSG);
  }

  private Map<Account.Id, Timestamp> scanAccounts(ReviewDb db, UpdateUI ui) throws SQLException {
    ui.message(String.format("... (%.3f s) scan accounts", elapsed()));
    try (Statement stmt = newStatement(db);
        ResultSet rs = stmt.executeQuery("SELECT account_id, registered_on FROM accounts")) {
      HashMap<Account.Id, Timestamp> m = new HashMap<>();
      while (rs.next()) {
        m.put(new Account.Id(rs.getInt(1)), rs.getTimestamp(2));
      }
      size = m.size();
      return m;
    }
  }
}
