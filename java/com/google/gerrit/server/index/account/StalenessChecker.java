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

package com.google.gerrit.server.index.account;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.StalenessCheckResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks if documents in the account index are stale.
 *
 * <p>An index document is considered stale if the stored ref state differs from the SHA1 of the
 * user branch or if the stored external ID states don't match with the external IDs of the account
 * from the refs/meta/external-ids branch.
 */
@Singleton
public class StalenessChecker {
  public static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(
          AccountField.ID.getName(),
          AccountField.REF_STATE.getName(),
          AccountField.EXTERNAL_ID_STATE.getName());

  public static final ImmutableSet<String> FIELDS2 =
      ImmutableSet.of(
          AccountField.ID_STR.getName(),
          AccountField.REF_STATE.getName(),
          AccountField.EXTERNAL_ID_STATE.getName());

  private final AccountIndexCollection indexes;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final ExternalIds externalIds;
  private final IndexConfig indexConfig;

  @Inject
  StalenessChecker(
      AccountIndexCollection indexes,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      ExternalIds externalIds,
      IndexConfig indexConfig) {
    this.indexes = indexes;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.externalIds = externalIds;
    this.indexConfig = indexConfig;
  }

  public StalenessCheckResult check(Account.Id id) throws IOException {
    AccountIndex i = indexes.getSearchIndex();
    if (i == null) {
      // No index; caller couldn't do anything if it is stale.
      return StalenessCheckResult.notStale();
    }
    if (!i.getSchema().hasField(AccountField.REF_STATE)
        || !i.getSchema().hasField(AccountField.EXTERNAL_ID_STATE)) {
      // Index version not new enough for this check.
      return StalenessCheckResult.notStale();
    }

    boolean useLegacyNumericFields = i.getSchema().useLegacyNumericFields();
    ImmutableSet<String> fields = useLegacyNumericFields ? FIELDS : FIELDS2;
    Optional<FieldBundle> result =
        i.getRaw(
            id,
            QueryOptions.create(
                indexConfig, 0, 1, IndexUtils.accountFields(fields, useLegacyNumericFields)));
    if (!result.isPresent()) {
      // The document is missing in the index.
      try (Repository repo = repoManager.openRepository(allUsersName)) {
        Ref ref = repo.exactRef(RefNames.refsUsers(id));

        // Stale if the account actually exists.
        if (ref == null) {
          return StalenessCheckResult.notStale();
        }
        return StalenessCheckResult.stale(
            "Document missing in index, but found %s in the repo", ref);
      }
    }

    for (Map.Entry<Project.NameKey, RefState> e :
        RefState.parseStates(result.get().getValue(AccountField.REF_STATE)).entries()) {
      // Custom All-Users repository names are not indexed. Instead, the default name is used.
      // Therefore, defer to the currently configured All-Users name.
      Project.NameKey repoName =
          e.getKey().get().equals(AllUsersNameProvider.DEFAULT) ? allUsersName : e.getKey();
      try (Repository repo = repoManager.openRepository(repoName)) {
        if (!e.getValue().match(repo)) {
          return StalenessCheckResult.stale(
              "Ref was modified since the account was indexed (%s != %s)",
              e.getValue(), repo.exactRef(e.getValue().ref()));
        }
      }
    }

    Set<ExternalId> extIds = externalIds.byAccount(id);
    ListMultimap<ObjectId, ObjectId> extIdStates =
        parseExternalIdStates(result.get().getValue(AccountField.EXTERNAL_ID_STATE));
    if (extIdStates.size() != extIds.size()) {
      return StalenessCheckResult.stale(
          "External IDs of the account were modified since the account was indexed. (%s != %s)",
          extIdStates.size(), extIds.size());
    }
    for (ExternalId extId : extIds) {
      if (!extIdStates.containsKey(extId.key().sha1())) {
        return StalenessCheckResult.stale("External ID missing: %s", extId.key().sha1());
      }
      if (!extIdStates.containsEntry(extId.key().sha1(), extId.blobId())) {
        return StalenessCheckResult.stale(
            "External ID has unexpected value. (%s != %s)",
            extIdStates.get(extId.key().sha1()), extId.blobId());
      }
    }

    return StalenessCheckResult.notStale();
  }

  public static ListMultimap<ObjectId, ObjectId> parseExternalIdStates(
      Iterable<byte[]> extIdStates) {
    ListMultimap<ObjectId, ObjectId> result = MultimapBuilder.hashKeys().arrayListValues().build();

    if (extIdStates == null) {
      return result;
    }

    for (byte[] b : extIdStates) {
      requireNonNull(b, "invalid external ID state");
      String s = new String(b, UTF_8);
      List<String> parts = Splitter.on(':').splitToList(s);
      checkState(parts.size() == 2, "invalid external ID state: %s", s);
      result.put(ObjectId.fromString(parts.get(0)), ObjectId.fromString(parts.get(1)));
    }
    return result;
  }
}
