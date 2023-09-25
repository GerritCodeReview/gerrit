// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.gerrit.entities.RefNames.REFS_SEQUENCES;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;

public class ProjectChangeSequence {
  public static final int FIRST_CHANGE_ID = 1;

  private final RepoSequence projectChangeSeq;
  private final int changeBatchSize;

  private static final String NAME_CHANGES = "changes";
  private static final String SECTION_NOTEDB = "noteDb";
  private static final String KEY_SEQUENCE_BATCH_SIZE = "sequenceBatchSize";
  private static final int DEFAULT_CHANGES_SEQUENCE_BATCH_SIZE = 20;

  public interface Factory {
    ProjectChangeSequence create(Project.NameKey projectNameKey);
  }

  @Inject
  public ProjectChangeSequence(
      @GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated,
      @Assisted Project.NameKey projectNameKey) {

    changeBatchSize =
        cfg.getInt(
            SECTION_NOTEDB,
            NAME_CHANGES,
            KEY_SEQUENCE_BATCH_SIZE,
            DEFAULT_CHANGES_SEQUENCE_BATCH_SIZE);

    projectChangeSeq =
        new RepoSequence(
            repoManager,
            gitRefUpdated,
            projectNameKey,
            NAME_CHANGES,
            () -> FIRST_CHANGE_ID,
            changeBatchSize);
  }

  public int nextChangeId() {
    return projectChangeSeq.next();
  }

  public ImmutableList<Integer> nextChangeIds(int count) {
    return projectChangeSeq.next(count);
  }

  public int changeBatchSize() {
    return changeBatchSize;
  }

  public int currentChangeId() {
    return projectChangeSeq.current();
  }

  public int lastChangeId() {
    return projectChangeSeq.last();
  }

  public void setChangeIdValue(int value) {
    projectChangeSeq.storeNew(value);
  }

  public static void initSequences(Repository git, BatchRefUpdate bru, int firstChangeId)
      throws IOException {
    if (git.exactRef(REFS_SEQUENCES + Sequences.NAME_CHANGES) == null) {
      // Can't easily reuse the inserter from MetaDataUpdate, but this shouldn't slow down site
      // initialization unduly.
      try (ObjectInserter ins = git.newObjectInserter()) {
        bru.addCommand(RepoSequence.storeNew(ins, Sequences.NAME_CHANGES, firstChangeId));
        ins.flush();
      }
    }
  }
}
