// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class DraftCommentsCacheImpl implements DraftCommentsCache {
  private static final String CACHE_NAME = "drafts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, Account.Id.class, new TypeLiteral<List<Change.Id>>() {})
            .maximumWeight(Long.MAX_VALUE)
            .loader(Loader.class);

        bind(DraftCommentsCacheImpl.class);
        bind(DraftCommentsCache.class).to(DraftCommentsCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, List<Change.Id>> cache;

  @Inject
  DraftCommentsCacheImpl(@Named(CACHE_NAME) LoadingCache<Account.Id, List<Change.Id>> cache) {
    this.cache = cache;
  }

  @Override
  public List<Change.Id> get(Account.Id accountId) {
    try {
      return cache.get(accountId);
    } catch (ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void evict(Account.Id accountId) {
    cache.invalidate(accountId);
  }

  /**
   * Loader for the list of change IDs containing draft comments for a user account. The cache loads
   * the change IDs by filtering the {@link RefNames#REFS_DRAFT_COMMENTS} in the All-Users repo
   * according to the account ID.
   */
  static class Loader extends CacheLoader<Account.Id, List<Change.Id>> {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;

    @Inject
    Loader(AllUsersName allUsers, GitRepositoryManager repoManager) {
      this.allUsers = allUsers;
      this.repoManager = repoManager;
    }

    @Override
    public List<Change.Id> load(Account.Id id) throws Exception {
      try (Repository repo = repoManager.openRepository(allUsers)) {
        return getChangesWithDrafts(repo, id);
      } catch (IOException e) {
        throw new StorageException(e);
      }
    }

    private List<Change.Id> getChangesWithDrafts(Repository repo, Account.Id accountId)
        throws IOException {
      Set<Change.Id> changes = new HashSet<>();
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS)) {
        Integer accountIdFromRef = RefNames.parseRefSuffix(ref.getName());
        if (accountIdFromRef != null && accountIdFromRef == accountId.get()) {
          Change.Id changeId = Change.Id.fromAllUsersRef(ref.getName());
          if (changeId == null) {
            continue;
          }
          changes.add(changeId);
        }
      }
      return changes.stream().collect(ImmutableList.toImmutableList());
    }
  }
}
