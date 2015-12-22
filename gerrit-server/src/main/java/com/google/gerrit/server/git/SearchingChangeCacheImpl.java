// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class SearchingChangeCacheImpl
    implements ChangeCache, GitReferenceUpdatedListener {
  private static final Logger log =
      LoggerFactory.getLogger(SearchingChangeCacheImpl.class);
  static final String ID_CACHE = "changes";
  static final String ID_CACHE_SINGLE = "changes_by_id";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeCache.class).to(SearchingChangeCacheImpl.class);
        cache(ID_CACHE,
            Project.NameKey.class,
            new TypeLiteral<List<Change>>() {})
          .maximumWeight(0)
          .loader(Loader.class);

        cache(ID_CACHE_SINGLE,
            Change.Id.class,
            Change.class)
          .maximumWeight(1024)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .loader(SingleChangeLoader.class);
      }
    };
  }

  private final LoadingCache<Project.NameKey, List<Change>> cache;
  private final LoadingCache<Id, Change> cacheById;

  @Inject
  SearchingChangeCacheImpl(
      @Named(ID_CACHE) LoadingCache<Project.NameKey, List<Change>> cache,
      @Named(ID_CACHE_SINGLE) LoadingCache<Change.Id, Change> cacheById) {
    this.cache = cache;
    this.cacheById = cacheById;
  }

  @Override
  public List<Change> get(Project.NameKey name) {
    try {
      return cache.get(name);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch changes for " + name, e);
      return Collections.emptyList();
    }
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    if (event.getRefName().startsWith(RefNames.REFS_CHANGES)) {
      cache.invalidate(new Project.NameKey(event.getProjectName()));
    }
  }

  static class Loader extends CacheLoader<Project.NameKey, List<Change>> {
    private final OneOffRequestContext requestContext;
    private final Provider<InternalChangeQuery> queryProvider;

    @Inject
    Loader(OneOffRequestContext requestContext,
        Provider<InternalChangeQuery> queryProvider) {
      this.requestContext = requestContext;
      this.queryProvider = queryProvider;
    }

    @Override
    public List<Change> load(Project.NameKey key) throws Exception {
      try (AutoCloseable ctx = requestContext.open()) {
        return Collections.unmodifiableList(
            ChangeData.asChanges(queryProvider.get().byProject(key)));
      }
    }
  }

  @Override
  public Change get(Change.Id id) {
    try {
      return cacheById.get(id);
    } catch (ExecutionException e) {
      log.error("Cannot retrieve change with id " + id, e);
      return null;
    }
  }

  @Override
  public void evict(Change.Id id) {
    Change change = cacheById.getIfPresent(id);
    if (change != null) {
      cacheById.invalidate(id);
      cache.invalidate(change.getProject());
    }
  }
}
