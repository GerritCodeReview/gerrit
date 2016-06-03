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

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ReviewerSet;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class SearchingChangeCacheImpl
    implements ChangeCache, GitReferenceUpdatedListener {
  private static final Logger log =
      LoggerFactory.getLogger(SearchingChangeCacheImpl.class);
  static final String ID_CACHE = "changes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ChangeCache.class).to(SearchingChangeCacheImpl.class);
        cache(ID_CACHE,
            Project.NameKey.class,
            new TypeLiteral<List<CachedChange>>() {})
          .maximumWeight(0)
          .loader(Loader.class);
      }
    };
  }

  @AutoValue
  static abstract class CachedChange {
    // Subset of fields in ChangeData, specifically fields needed to serve
    // VisibleRefFilter without touching the database. More can be added as
    // necessary.
    abstract Change change();
    @Nullable abstract ReviewerSet reviewers();
  }

  private final LoadingCache<Project.NameKey, List<CachedChange>> cache;
  private final ChangeData.Factory changeDataFactory;

  @Inject
  SearchingChangeCacheImpl(
      @Named(ID_CACHE) LoadingCache<Project.NameKey, List<CachedChange>> cache,
      ChangeData.Factory changeDataFactory) {
    this.cache = cache;
    this.changeDataFactory = changeDataFactory;
  }

  @Override
  public List<Change> get(Project.NameKey name) {
    try {
      return Lists.transform(
          cache.get(name),
          new Function<CachedChange, Change>() {
            @Override
            public Change apply(CachedChange in) {
              return in.change();
            }
          });
    } catch (ExecutionException e) {
      log.warn("Cannot fetch changes for " + name, e);
      return Collections.emptyList();
    }
  }

  // TODO(dborowitz): I think VisibleRefFilter is the only user of ChangeCache
  // at all; probably we can just stop implementing that interface entirely.
  public List<ChangeData> getChangeData(ReviewDb db, Project.NameKey name) {
    try {
      List<CachedChange> cached = cache.get(name);
      List<ChangeData> cds = new ArrayList<>(cached.size());
      for (CachedChange cc : cached) {
        ChangeData cd = changeDataFactory.create(db, cc.change());
        cd.setReviewers(cc.reviewers());
        cds.add(cd);
      }
      return Collections.unmodifiableList(cds);
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

  static class Loader extends CacheLoader<Project.NameKey, List<CachedChange>> {
    private final OneOffRequestContext requestContext;
    private final Provider<InternalChangeQuery> queryProvider;

    @Inject
    Loader(OneOffRequestContext requestContext,
        Provider<InternalChangeQuery> queryProvider) {
      this.requestContext = requestContext;
      this.queryProvider = queryProvider;
    }

    @Override
    public List<CachedChange> load(Project.NameKey key) throws Exception {
      try (AutoCloseable ctx = requestContext.open()) {
        List<ChangeData> cds = queryProvider.get().byProject(key);
        List<CachedChange> result = new ArrayList<>(cds.size());
        for (ChangeData cd : cds) {
          result.add(new AutoValue_SearchingChangeCacheImpl_CachedChange(
              cd.change(), cd.getReviewers()));
        }
        return Collections.unmodifiableList(result);
      }
    }
  }
}
