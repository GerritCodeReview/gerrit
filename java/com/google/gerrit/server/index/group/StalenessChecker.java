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
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.StalenessCheckResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks if documents in the group index are stale.
 *
 * <p>An index document is considered stale if the stored SHA1 differs from the HEAD SHA1 of the
 * groups branch.
 */
@Singleton
public class StalenessChecker {
  public static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(GroupField.UUID_FIELD_SPEC.getName(), GroupField.REF_STATE_SPEC.getName());

  private final GroupIndexCollection indexes;
  private final GitRepositoryManager repoManager;
  private final IndexConfig indexConfig;
  private final AllUsersName allUsers;

  @Inject
  StalenessChecker(
      GroupIndexCollection indexes,
      GitRepositoryManager repoManager,
      IndexConfig indexConfig,
      AllUsersName allUsers) {
    this.indexes = indexes;
    this.repoManager = repoManager;
    this.indexConfig = indexConfig;
    this.allUsers = allUsers;
  }

  public StalenessCheckResult check(AccountGroup.UUID uuid) throws IOException {
    GroupIndex i = indexes.getSearchIndex();
    if (i == null) {
      // No index; caller couldn't do anything if it is stale.
      return StalenessCheckResult.notStale();
    }

    Optional<FieldBundle> result =
        i.getRaw(uuid, IndexedGroupQuery.createOptions(indexConfig, 0, 1, 1, FIELDS));
    if (!result.isPresent()) {
      // The document is missing in the index.
      try (Repository repo = repoManager.openRepository(allUsers)) {
        Ref ref = repo.exactRef(RefNames.refsGroups(uuid));

        // Stale if the group actually exists.
        if (ref == null) {
          return StalenessCheckResult.notStale();
        }
        return StalenessCheckResult.stale(
            "Document missing in index, but found %s in the repo", ref);
      }
    }

    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(RefNames.refsGroups(uuid));
      ObjectId head = ref == null ? ObjectId.zeroId() : ref.getObjectId();
      ObjectId idFromIndex =
          ObjectId.fromString(result.get().getValue(GroupField.REF_STATE_SPEC), 0);
      if (head.equals(idFromIndex)) {
        return StalenessCheckResult.notStale();
      }
      return StalenessCheckResult.stale(
          "Document has unexpected ref state (%s != %s)", head, idFromIndex);
    }
  }
}
