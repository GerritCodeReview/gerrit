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
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Cache based on an index query of the most recent changes. The number of cached items depends on
 * the index implementation and configuration.
 *
 * <p>This cache is intended to be used when filtering references. By design it returns only a
 * fraction of all changes. These are the changes that were modified last.
 */
@Singleton
public class SearchingChangeCacheImpl implements GitReferenceUpdatedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String ID_CACHE = "changes";

  public static class Module extends CacheModule {
    private final boolean slave;

    public Module() {
      this(false);
    }

    public Module(boolean slave) {
      this.slave = slave;
    }

    @Override
    protected void configure() {
      if (slave) {
        bind(SearchingChangeCacheImpl.class).toProvider(Providers.of(null));
      } else {
        cache(ID_CACHE, Project.NameKey.class, new TypeLiteral<List<CachedChange>>() {})
            .maximumWeight(0)
            .loader(Loader.class);

        bind(SearchingChangeCacheImpl.class);
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
            .to(SearchingChangeCacheImpl.class);
      }
    }
  }

  @AutoValue
  @UsedAt(UsedAt.Project.GOOGLE)
  public abstract static class CachedChange {
    // Subset of fields in ChangeData, specifically fields needed to serve
    // VisibleRefFilter without touching the database. More can be added as
    // necessary.
    abstract Change change();

    @Nullable
    abstract ReviewerSet reviewers();
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

  /**
   * Read changes for the project from the secondary index.
   *
   * <p>Returned changes only include the {@code Change} object (with id, branch) and the reviewers.
   * Additional stored fields are not loaded from the index.
   *
   * @param project project to read.
   * @return list of known changes; empty if no changes.
   */
  public List<ChangeData> getChangeData(Project.NameKey project) {
    try {
      List<CachedChange> cached = cache.get(project);
      List<ChangeData> cds = new ArrayList<>(cached.size());
      for (CachedChange cc : cached) {
        ChangeData cd = changeDataFactory.create(cc.change());
        cd.setReviewers(cc.reviewers());
        cds.add(cd);
      }
      return Collections.unmodifiableList(cds);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot fetch changes for %s", project);
      return Collections.emptyList();
    }
  }

  @Override
  public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) {
    if (event.getRefName().startsWith(RefNames.REFS_CHANGES)) {
      cache.invalidate(Project.nameKey(event.getProjectName()));
    }
  }

  static class Loader extends CacheLoader<Project.NameKey, List<CachedChange>> {
    private final OneOffRequestContext requestContext;
    private final Provider<InternalChangeQuery> queryProvider;

    @Inject
    Loader(OneOffRequestContext requestContext, Provider<InternalChangeQuery> queryProvider) {
      this.requestContext = requestContext;
      this.queryProvider = queryProvider;
    }

    @Override
    public List<CachedChange> load(Project.NameKey key) throws Exception {
      try (TraceTimer timer =
              TraceContext.newTimer(
                  "Loading changes of project", Metadata.builder().projectName(key.get()).build());
          ManualRequestContext ctx = requestContext.open()) {
        List<ChangeData> cds =
            queryProvider
                .get()
                .setRequestedFields(ChangeField.CHANGE, ChangeField.REVIEWER)
                .byProject(key);
        List<CachedChange> result = new ArrayList<>(cds.size());
        for (ChangeData cd : cds) {
          result.add(
              new AutoValue_SearchingChangeCacheImpl_CachedChange(cd.change(), cd.getReviewers()));
        }
        return Collections.unmodifiableList(result);
      }
    }
  }
}
