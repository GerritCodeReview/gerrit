// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class AllUsersRefToObjectIdCache implements GitReferenceUpdatedListener {
  private static final String BYREF = "all_users_objects";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYREF, String.class, new TypeLiteral<Optional<ObjectId>>() {}).loader(Loader.class);
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
            .to(AllUsersRefToObjectIdCache.class);
      }
    };
  }

  private final LoadingCache<String, Optional<ObjectId>> refObjectCache;
  private final AllUsersName allUsersName;

  @Inject
  AllUsersRefToObjectIdCache(
      @Named(BYREF) LoadingCache<String, Optional<ObjectId>> refObjectCache,
      AllUsersName allUsersName) {
    this.refObjectCache = refObjectCache;
    this.allUsersName = allUsersName;
  }

  Optional<ObjectId> get(String ref) {
    return refObjectCache.getUnchecked(ref);
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    if (allUsersName.get().equals(event.getProjectName())
        && event.getRefName().startsWith(RefNames.REFS_USERS)) {
      refObjectCache.invalidate(event.getRefName());
    }
  }

  public void evictAccount(Account.Id id) {
    evict(RefNames.refsUsers(id));
  }

  public void evictPreferences() {
    evict(RefNames.REFS_USERS_DEFAULT);
  }

  private void evict(String ref) {
    refObjectCache.invalidate(ref);
  }

  @Singleton
  static class Loader extends CacheLoader<String, Optional<ObjectId>> {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;

    @Inject
    Loader(GitRepositoryManager repoManager, AllUsersName allUsersName) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
    }

    @Override
    public Optional<ObjectId> load(String ref) throws Exception {
      try (Repository allUsers = repoManager.openRepository(allUsersName)) {
        return Optional.ofNullable(allUsers.exactRef(ref)).map(Ref::getObjectId);
      }
    }
  }
}
