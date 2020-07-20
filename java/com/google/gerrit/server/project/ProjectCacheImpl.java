// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.CacheRefreshExecutor;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCacheImpl implements ProjectCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String CACHE_NAME = "projects";

  private static final String CACHE_LIST = "project_list";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, Project.NameKey.class, CachedProjectConfig.class)
            .loader(Loader.class)
            .refreshAfterWrite(Duration.ofMinutes(15))
            .expireAfterWrite(Duration.ofHours(1));

        cache(CACHE_LIST, ListKey.class, new TypeLiteral<ImmutableSortedSet<Project.NameKey>>() {})
            .maximumWeight(1)
            .loader(Lister.class);

        bind(ProjectCacheImpl.class);
        bind(ProjectCache.class).to(ProjectCacheImpl.class);

        install(
            new LifecycleModule() {
              @Override
              protected void configure() {
                listener().to(ProjectCacheWarmer.class);
              }
            });
      }
    };
  }

  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final LoadingCache<Project.NameKey, CachedProjectConfig> byName;
  private final LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list;
  private final Lock listLock;
  private final Provider<ProjectIndexer> indexer;
  private final Timer0 guessRelevantGroupsLatency;
  private final ProjectState.Factory projectStateFactory;

  @Inject
  ProjectCacheImpl(
      final AllProjectsName allProjectsName,
      final AllUsersName allUsersName,
      @Named(CACHE_NAME) LoadingCache<Project.NameKey, CachedProjectConfig> byName,
      @Named(CACHE_LIST) LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list,
      Provider<ProjectIndexer> indexer,
      MetricMaker metricMaker,
      ProjectState.Factory projectStateFactory) {
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.byName = byName;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);
    this.indexer = indexer;
    this.projectStateFactory = projectStateFactory;

    this.guessRelevantGroupsLatency =
        metricMaker.newTimer(
            "group/guess_relevant_groups_latency",
            new Description("Latency for guessing relevant groups")
                .setCumulative()
                .setUnit(Units.NANOSECONDS));
  }

  @Override
  public ProjectState getAllProjects() {
    return get(allProjectsName).orElseThrow(illegalState(allProjectsName));
  }

  @Override
  public ProjectState getAllUsers() {
    return get(allUsersName).orElseThrow(illegalState(allUsersName));
  }

  @Override
  public Optional<ProjectState> get(@Nullable Project.NameKey projectName) {
    if (projectName == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(byName.get(projectName)).map(projectStateFactory::create);
    } catch (ExecutionException e) {
      if ((e.getCause() instanceof RepositoryNotFoundException)) {
        logger.atFine().log("Cannot find project %s", projectName.get());
        return Optional.empty();
      }
      throw new StorageException(
          String.format("project state of project %s not available", projectName.get()), e);
    }
  }

  @Override
  public void evict(Project p) {
    evict(p.getNameKey());
  }

  @Override
  public void evict(Project.NameKey p) {
    if (p != null) {
      logger.atFine().log("Evict project '%s'", p.get());
      byName.invalidate(p);
    }
    indexer.get().index(p);
  }

  @Override
  public void remove(Project p) {
    remove(p.getNameKey());
  }

  @Override
  public void remove(Project.NameKey name) {
    listLock.lock();
    try {
      list.put(
          ListKey.ALL,
          ImmutableSortedSet.copyOf(Sets.difference(list.get(ListKey.ALL), ImmutableSet.of(name))));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot list available projects");
    } finally {
      listLock.unlock();
    }
    evict(name);
  }

  @Override
  public void onCreateProject(Project.NameKey newProjectName) throws IOException {
    listLock.lock();
    try {
      list.put(
          ListKey.ALL,
          ImmutableSortedSet.copyOf(
              Sets.union(list.get(ListKey.ALL), ImmutableSet.of(newProjectName))));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot list available projects");
    } finally {
      listLock.unlock();
    }
    indexer.get().index(newProjectName);
  }

  @Override
  public ImmutableSortedSet<Project.NameKey> all() {
    try {
      return list.get(ListKey.ALL);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot list available projects");
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public Set<AccountGroup.UUID> guessRelevantGroupUUIDs() {
    try (Timer0.Context ignored = guessRelevantGroupsLatency.start()) {
      return all().stream()
          .map(n -> byName.getIfPresent(n))
          .filter(Objects::nonNull)
          .flatMap(p -> p.getAllGroupUUIDs().stream())
          // getAllGroupUUIDs shouldn't really return null UUIDs, but harden
          // against them just in case there is a bug or corner case.
          .filter(id -> id != null && id.get() != null)
          .collect(toSet());
    }
  }

  @Override
  public ImmutableSortedSet<Project.NameKey> byName(String pfx) {
    Project.NameKey start = Project.nameKey(pfx);
    Project.NameKey end = Project.nameKey(pfx + Character.MAX_VALUE);
    try {
      // Right endpoint is exclusive, but U+FFFF is a non-character so no project ends with it.
      return list.get(ListKey.ALL).subSet(start, end);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up projects for prefix %s", pfx);
      return ImmutableSortedSet.of();
    }
  }

  @Singleton
  static class Loader extends CacheLoader<Project.NameKey, CachedProjectConfig> {
    private final ProjectState.Factory projectStateFactory;
    private final GitRepositoryManager mgr;
    private final ProjectConfig.Factory projectConfigFactory;
    private final ListeningExecutorService cacheRefreshExecutor;
    private final Counter2<String, Boolean> refreshCounter;

    @Inject
    Loader(
        ProjectState.Factory psf,
        GitRepositoryManager g,
        ProjectConfig.Factory projectConfigFactory,
        @CacheRefreshExecutor ListeningExecutorService cacheRefreshExecutor,
        MetricMaker metricMaker) {
      projectStateFactory = psf;
      mgr = g;
      this.projectConfigFactory = projectConfigFactory;
      this.cacheRefreshExecutor = cacheRefreshExecutor;
      refreshCounter =
          metricMaker.newCounter(
              "caches/refresh_count",
              new Description("count").setRate(),
              Field.ofString("cache", Metadata.Builder::className).build(),
              Field.ofBoolean("outdated", Metadata.Builder::outdated).build());
    }

    @Override
    public CachedProjectConfig load(Project.NameKey key) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading project", Metadata.builder().projectName(key.get()).build())) {
        try (Repository git = mgr.openRepository(key)) {
          ProjectConfig cfg = projectConfigFactory.create(key);
          cfg.load(key, git);
          return cfg.getCacheable();
        }
      }
    }

    @Override
    public ListenableFuture<CachedProjectConfig> reload(
        Project.NameKey key, CachedProjectConfig oldState) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Reload project", Metadata.builder().projectName(key.get()).build())) {
        try (Repository git = mgr.openRepository(key)) {
          Ref configRef = git.exactRef(RefNames.REFS_CONFIG);
          if (configRef != null && configRef.getObjectId().equals(oldState.getRevision().get())) {
            refreshCounter.increment(CACHE_NAME, false);
            return Futures.immediateFuture(oldState);
          }
        }

        // Repository is not thread safe, so we have to open it on the thread that does the loading.
        // Just invoke the loader on the other thread.
        refreshCounter.increment(CACHE_NAME, true);
        return cacheRefreshExecutor.submit(() -> load(key));
      }
    }
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {}
  }

  static class Lister extends CacheLoader<ListKey, ImmutableSortedSet<Project.NameKey>> {
    private final GitRepositoryManager mgr;

    @Inject
    Lister(GitRepositoryManager mgr) {
      this.mgr = mgr;
    }

    @Override
    public ImmutableSortedSet<Project.NameKey> load(ListKey key) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading project list")) {
        return ImmutableSortedSet.copyOf(mgr.list());
      }
    }
  }

  @VisibleForTesting
  public void evictAllByName() {
    byName.invalidateAll();
  }

  @VisibleForTesting
  public long sizeAllByName() {
    return byName.size();
  }
}
