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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class Sequences {
  public static final String NAME_ACCOUNTS = "accounts";
  public static final String NAME_GROUPS = "groups";
  public static final String NAME_CHANGES = "changes";

  public static final int FIRST_ACCOUNT_ID = 1000000;
  public static final int FIRST_GROUP_ID = 1;
  public static final int FIRST_CHANGE_ID = 1;

  private enum SequenceType {
    ACCOUNTS,
    CHANGES,
    GROUPS;
  }

  private final RepoSequence accountSeq;
  private final RepoSequence changeSeq;
  private final RepoSequence groupSeq;
  private final Timer2<SequenceType, Boolean> nextIdLatency;
  private final Iterable<ChangeNumberValidator> changeNumberValidators;

  @Inject
  Sequences(
      @GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      MetricMaker metrics,
      DynamicSet<ChangeNumberValidator> changeNumberValidators) {
    this(
        cfg,
        repoManager,
        gitRefUpdated,
        allProjects,
        allUsers,
        metrics,
        (Iterable<ChangeNumberValidator>) changeNumberValidators);
  }

  public Sequences(
      Config cfg,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      MetricMaker metrics,
      Iterable<ChangeNumberValidator> changeNumberValidators) {
    int accountBatchSize = cfg.getInt("noteDb", "accounts", "sequenceBatchSize", 1);
    accountSeq =
        new RepoSequence(
            repoManager,
            gitRefUpdated,
            allUsers,
            NAME_ACCOUNTS,
            () -> FIRST_ACCOUNT_ID,
            accountBatchSize);

    int changeBatchSize = cfg.getInt("noteDb", "changes", "sequenceBatchSize", 20);
    changeSeq =
        new RepoSequence(
            repoManager,
            gitRefUpdated,
            allProjects,
            NAME_CHANGES,
            () -> FIRST_CHANGE_ID,
            changeBatchSize);

    int groupBatchSize = 1;
    groupSeq =
        new RepoSequence(
            repoManager,
            gitRefUpdated,
            allUsers,
            NAME_GROUPS,
            () -> FIRST_GROUP_ID,
            groupBatchSize);

    nextIdLatency =
        metrics.newTimer(
            "sequence/next_id_latency",
            new Description("Latency of requesting IDs from repo sequences")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            Field.ofEnum(SequenceType.class, "sequence"),
            Field.ofBoolean("multiple"));

    this.changeNumberValidators = changeNumberValidators;
  }

  public int nextAccountId() throws OrmException {
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.ACCOUNTS, false)) {
      return accountSeq.next();
    }
  }

  public int nextChangeId() throws OrmException {
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.CHANGES, false)) {
      return nextChangeIdImpl(ImmutableList.copyOf(changeNumberValidators));
    }
  }

  private int nextChangeIdImpl(ImmutableList<ChangeNumberValidator> validators)
      throws OrmException {
    int n = changeSeq.next();
    while (!isValid(validators, n)) {
      n = changeSeq.next();
    }
    return n;
  }

  private static boolean isValid(ImmutableList<ChangeNumberValidator> validators, int candidate)
      throws OrmException {
    for (ChangeNumberValidator validator : validators) {
      if (!validator.isValidChangeNumber(candidate)) {
        return false;
      }
    }
    return true;
  }

  public ImmutableList<Integer> nextChangeIds(int count) throws OrmException {
    checkArgument(count >= 0, "count must be positive: " + count);
    if (count == 0) {
      return ImmutableList.of();
    }
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.CHANGES, count > 1)) {
      ImmutableList<Integer> candidates = changeSeq.next(count);
      ImmutableList<ChangeNumberValidator> validators =
          ImmutableList.copyOf(changeNumberValidators);
      if (validators.isEmpty()) {
        return candidates;
      }

      int need = 0;
      ImmutableList.Builder<Integer> result = ImmutableList.builderWithExpectedSize(count);
      for (int candidate : candidates) {
        if (isValid(validators, candidate)) {
          result.add(candidate);
        } else {
          need++;
        }
      }
      for (int i = 0; i < need; i++) {
        result.add(nextChangeIdImpl(validators));
      }
      return result.build();
    }
  }

  public int nextGroupId() throws OrmException {
    try (Timer2.Context timer = nextIdLatency.start(SequenceType.GROUPS, false)) {
      return groupSeq.next();
    }
  }
}
