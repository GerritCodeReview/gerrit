// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;

@Singleton
public class Sequences {
  public static final String NAME_ACCOUNTS = "accounts";
  public static final String NAME_GROUPS = "groups";
  public static final String NAME_CHANGES = "changes";

  public static int getChangeSequenceGap(Config cfg) {
    return cfg.getInt("noteDb", "changes", "initialSequenceGap", 1000);
  }

  private enum SequenceType {
    ACCOUNTS,
    CHANGES,
    GROUPS;
  }

  private final Provider<ReviewDb> db;
  private final NotesMigration migration;
  private final RepoSequence accountSeq;
  private final RepoSequence changeSeq;
  private final RepoSequence groupSeq;
  private final Timer2<SequenceType, Boolean> nextIdLatency;

  @Inject
  public Sequences(
      @GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      NotesMigration migration,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      MetricMaker metrics) {
    this.db = db;
    this.migration = migration;

    int accountBatchSize = cfg.getInt("noteDb", "accounts", "sequenceBatchSize", 1);
    accountSeq =
        new RepoSequence(
            repoManager,
            gitRefUpdated,
            allUsers,
            NAME_ACCOUNTS,
            () -> ReviewDb.FIRST_ACCOUNT_ID,
            accountBatchSize);

    int gap = getChangeSequenceGap(cfg);
    @SuppressWarnings("deprecation")
    RepoSequence.Seed changeSeed = () -> db.get().nextChangeId() + gap;
    int changeBatchSize = cfg.getInt("noteDb", "changes", "sequenceBatchSize", 20);
    changeSeq =
        new RepoSequence(
            repoManager, gitRefUpdated, allProjects, NAME_CHANGES, changeSeed, changeBatchSize);

    RepoSequence.Seed groupSeed = () -> nextGroupId(db.get());
    int groupBatchSize = 1;
    groupSeq =
        new RepoSequence(
            repoManager, gitRefUpdated, allUsers, NAME_GROUPS, groupSeed, groupBatchSize);

    nextIdLatency =
        metrics.newTimer(
            "sequence/next_id_latency",
            new Description("Latency of requesting IDs from repo sequences")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            Field.ofEnum(SequenceType.class, "sequence"),
            Field.ofBoolean("multiple"));
  }

  public int nextAccountId() throws OrmException {
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.ACCOUNTS, false)) {
      return accountSeq.next();
    }
  }

  public int nextChangeId() throws OrmException {
    if (!migration.readChangeSequence()) {
      return nextChangeId(db.get());
    }
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.CHANGES, false)) {
      return changeSeq.next();
    }
  }

  public ImmutableList<Integer> nextChangeIds(int count) throws OrmException {
    if (migration.readChangeSequence()) {
      try (Timer2.Context timer = nextIdLatency.start(SequenceType.CHANGES, count > 1)) {
        return changeSeq.next(count);
      }
    }

    if (count == 0) {
      return ImmutableList.of();
    }
    checkArgument(count > 0, "count is negative: %s", count);
    List<Integer> ids = new ArrayList<>(count);
    ReviewDb db = this.db.get();
    for (int i = 0; i < count; i++) {
      ids.add(nextChangeId(db));
    }
    return ImmutableList.copyOf(ids);
  }

  public int nextGroupId() throws OrmException {
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.GROUPS, false)) {
      return groupSeq.next();
    }
  }

  @VisibleForTesting
  public RepoSequence getChangeIdRepoSequence() {
    return changeSeq;
  }

  @SuppressWarnings("deprecation")
  private static int nextChangeId(ReviewDb db) throws OrmException {
    return db.nextChangeId();
  }

  @SuppressWarnings("deprecation")
  static int nextGroupId(ReviewDb db) throws OrmException {
    return db.nextAccountGroupId();
  }
}
