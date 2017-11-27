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

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.group.Rebuild.Input;
import com.google.gerrit.server.group.db.GroupBundle;
import com.google.gerrit.server.group.db.GroupRebuilder;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gerrit.server.update.RefUpdateUtil;
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
  public static class Input {
    public Boolean force;
  }

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
    boolean force = firstNonNull(input.force, false);
    if (!migration.writeToNoteDb()) {
      throw new MethodNotAllowedException("NoteDb writes must be enabled");
    }
    if (migration.readFromNoteDb() && force) {
      throw new MethodNotAllowedException("NoteDb reads must not be enabled when force=true");
    }
    if (!rsrc.isInternalGroup()) {
      throw new MethodNotAllowedException("Not an internal group");
    }

    AccountGroup.UUID uuid = rsrc.getGroup().getGroupUUID();
    try (Repository repo = repoManager.openRepository(allUsers)) {
      if (force) {
        RefUpdateUtil.deleteChecked(repo, RefNames.refsGroups(uuid));
      }
      GroupBundle reviewDbBundle =
          bundleFactory.fromReviewDb(db.get(), rsrc.asInternalGroup().get().getId());
      try {
        rebuilder.rebuild(repo, reviewDbBundle, null, firstNonNull(input.force, false));
      } catch (LockFailureException e) {
        throw new ResourceConflictException("Rebuild failed with lock failure");
      }

      GroupBundle noteDbBundle = bundleFactory.fromNoteDb(repo, uuid);

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
