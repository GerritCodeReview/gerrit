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

package com.google.gerrit.server.group;

import static java.util.stream.Collectors.joining;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.group.db.GroupBundle;
import com.google.gerrit.server.group.db.GroupRebuilder;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class Rebuild implements RestModifyView<GroupResource, Input> {
  private final AllUsersName allUsers;
  private final GitRepositoryManager repoManager;
  private final GroupBundle.Factory bundleFactory;
  private final GroupRebuilder rebuilder;
  private final GroupsMigration migration;
  private final Provider<ReviewDb> db;

  @Inject
  Rebuild(
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      GroupBundle.Factory bundleFactory,
      GroupRebuilder rebuilder,
      GroupsMigration migration,
      Provider<ReviewDb> db) {
    this.allUsers = allUsers;
    this.repoManager = repoManager;
    this.bundleFactory = bundleFactory;
    this.rebuilder = rebuilder;
    this.migration = migration;
    this.db = db;
  }

  @Override
  public BinaryResult apply(GroupResource rsrc, Input input)
      throws RestApiException, ConfigInvalidException, OrmException, IOException {
    if (!migration.writeToNoteDb()) {
      throw new BadRequestException("NoteDb writes must be enabled");
    }
    if (!rsrc.isInternalGroup()) {
      throw new BadRequestException("not an internal group");
    }
    try (Repository repo = repoManager.openRepository(allUsers)) {
      GroupBundle reviewDbBundle =
          bundleFactory.fromReviewDb(db.get(), rsrc.asInternalGroup().get().getId());
      try {
        rebuilder.rebuild(repo, reviewDbBundle, null);
      } catch (LockFailureException e) {
        throw new ResourceConflictException("rebuild failed with lock failure");
      }

      repo.scanForRepoChanges();
      GroupBundle noteDbBundle = bundleFactory.fromNoteDb(repo, rsrc.getGroup().getGroupUUID());

      List<String> diffs = GroupBundle.compare(reviewDbBundle, noteDbBundle);
      if (diffs.isEmpty()) {
        return BinaryResult.create("No differences between ReviewDb and NoteDb");
      }
      return BinaryResult.create(
          diffs
              .stream()
              .collect(joining("\n", "Differences between ReviewDb and NoteDb:\n", "\n")));
    }
  }
}
