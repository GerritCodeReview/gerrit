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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;

/**
 * Cache based on an index query of the most recent changes. The number of cached items depends on
 * the index implementation and configuration.
 *
 * <p>This cache is intended to be used when filtering references. By design it returns only a
 * fraction of all changes. These are the changes that were modified last.
 */
@Singleton
public class SearchingChangeCacheImpl
    implements ChangesByProjectCache, GitReferenceUpdatedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String ID_CACHE = "changes";

  public static class SearchingChangeCacheImplModule extends CacheModule {
    @Override
    protected void configure() {
      cache(ID_CACHE, Project.NameKey.class, new TypeLiteral<List<CachedChange>>() {})
          .maximumWeight(0)
          .loader(Loader.class);

      bind(ChangesByProjectCache.class).to(SearchingChangeCacheImpl.class);
      DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
          .to(SearchingChangeCacheImpl.class);
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
   * @param unusedrepo repository for the project to read.
   * @return stream of known changes; empty if no changes.
   */
  @Override
  public Stream<ChangeData> streamChangeDatas(Project.NameKey project, Repository unusedrepo) {
    List<CachedChange> cached;
    try {
      cached = cache.get(project);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot fetch changes for %s", project);
      return Stream.empty();
    }
    return cached.stream()
        .map(
            cc -> {
              ChangeData cd = changeDataFactory.create(cc.change());
              cd.setReviewers(cc.reviewers());
              return cd;
            });
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
                .setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.REVIEWER_SPEC)
                .byProject(key);
        Map<Change.Id, CachedChange> result = new HashMap<>(cds.size());
        for (ChangeData cd : cds) {
          if (result.containsKey(cd.getId())) {
            logger.atWarning().log(
                "Duplicate changes returned from change query by project %s: %s, %s",
                key, cd.change(), result.get(cd.getId()).change());
          }
          result.put(
              cd.getId(),
              new AutoValue_SearchingChangeCacheImpl_CachedChange(cd.change(), cd.reviewers()));
        }
        return List.copyOf(result.values());
      }
    }
  }
}
