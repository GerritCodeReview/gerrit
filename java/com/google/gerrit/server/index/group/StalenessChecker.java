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

package com.google.gerrit.server.index.group;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.FieldsBundle;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks if documents in the group index are stale.
 *
 * <p>An index document is considered stale if the stored SHA1 differs from the HEAD SHA1 of the
 * groups branch.
 *
 * <p>Note: This only applies to NoteDb.
 */
@Singleton
public class StalenessChecker {
  public static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(GroupField.UUID.getName(), GroupField.REF_STATE.getName());

  private final GroupIndexCollection indexes;
  private final GitRepositoryManager repoManager;
  private final IndexConfig indexConfig;
  private final AllUsersName allUsers;
  private final Config config;

  @Inject
  StalenessChecker(
      GroupIndexCollection indexes,
      GitRepositoryManager repoManager,
      IndexConfig indexConfig,
      AllUsersName allUsers,
      @GerritServerConfig Config config) {
    this.indexes = indexes;
    this.repoManager = repoManager;
    this.indexConfig = indexConfig;
    this.allUsers = allUsers;
    this.config = config;
  }

  public boolean isStale(AccountGroup.UUID id) throws IOException, OrmException {
    if (!config.getBoolean("user", "readGroupsFromNoteDb", false)) {
      return false; // This class only treats staleness for groups in NoteDb.
    }

    GroupIndex i = indexes.getSearchIndex();
    if (i == null) {
      return false; // No index; caller couldn't do anything if it is stale.
    }
    if (!i.getSchema().hasField(GroupField.REF_STATE)) {
      return false; // Index version not new enough for this check.
    }

    Optional<FieldsBundle> result =
        i.getRaw(id, IndexedGroupQuery.createOptions(indexConfig, 0, 1, FIELDS));
    if (!result.isPresent()) {
      return true;
    }

    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.findRef(RefNames.refsGroups(id));
      ObjectId head = ref == null ? ObjectId.zeroId() : ref.getObjectId();
      return !head.equals(ObjectId.fromRaw(result.get().getValue(GroupField.REF_STATE)));
    }
  }
}
