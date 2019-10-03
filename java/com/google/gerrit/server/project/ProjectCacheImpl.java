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

import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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
        cache(CACHE_NAME, String.class, ProjectState.class).loader(Loader.class);

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
                listener().to(ProjectCacheClock.class);
              }
            });
      }
    };
  }

  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final LoadingCache<String, ProjectState> byName;
  private final LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list;
  private final Lock listLock;
  private final ProjectCacheClock clock;
  private final Provider<ProjectIndexer> indexer;
  private final Timer0 guessRelevantGroupsLatency;

  @Inject
  ProjectCacheImpl(
      final AllProjectsName allProjectsName,
      final AllUsersName allUsersName,
      @Named(CACHE_NAME) LoadingCache<String, ProjectState> byName,
      @Named(CACHE_LIST) LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list,
      ProjectCacheClock clock,
      Provider<ProjectIndexer> indexer,
      MetricMaker metricMaker) {
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.byName = byName;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);
    this.clock = clock;
    this.indexer = indexer;

    this.guessRelevantGroupsLatency =
        metricMaker.newTimer(
            "group/guess_relevant_groups_latency",
            new Description("Latency for guessing relevant groups")
                .setCumulative()
                .setUnit(Units.NANOSECONDS));
  }

  @Override
  public ProjectState getAllProjects() {
    ProjectState state = get(allProjectsName);
    if (state == null) {
      // This should never occur, the server must have this
      // project to process anything.
      throw new IllegalStateException("Missing project " + allProjectsName);
    }
    return state;
  }

  @Override
  public ProjectState getAllUsers() {
    ProjectState state = get(allUsersName);
    if (state == null) {
      // This should never occur.
      throw new IllegalStateException("Missing project " + allUsersName);
    }
    return state;
  }

  @Override
  public ProjectState get(Project.NameKey projectName) {
    try {
      return checkedGet(projectName);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Cannot read project %s", projectName);
      return null;
    }
  }

  @Override
  public ProjectState checkedGet(Project.NameKey projectName) throws IOException {
    if (projectName == null) {
      return null;
    }
    try {
      return strictCheckedGet(projectName);
    } catch (Exception e) {
      if (!(e.getCause() instanceof RepositoryNotFoundException)) {
        logger.atWarning().withCause(e).log("Cannot read project %s", projectName.get());
        if (e.getCause() != null) {
          Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
        }
        throw new IOException(e);
      }
      logger.atFine().withCause(e).log("Cannot find project %s", projectName.get());
      return null;
    }
  }

  @Override
  public ProjectState checkedGet(Project.NameKey projectName, boolean strict) throws Exception {
    return strict ? strictCheckedGet(projectName) : checkedGet(projectName);
  }

  private ProjectState strictCheckedGet(Project.NameKey projectName) throws Exception {
    ProjectState state = byName.get(projectName.get());
    if (state != null && state.needsRefresh(clock.read())) {
      byName.invalidate(projectName.get());
      state = byName.get(projectName.get());
    }
    return state;
  }

  @Override
  public void evict(Project p) throws IOException {
    evict(p.getNameKey());
  }

  @Override
  public void evict(Project.NameKey p) throws IOException {
    if (p != null) {
      logger.atFine().log("Evict project '%s'", p.get());
      byName.invalidate(p.get());
    }
    indexer.get().index(p);
  }

  @Override
  public void remove(Project p) throws IOException {
    remove(p.getNameKey());
  }

  @Override
  public void remove(Project.NameKey name) throws IOException {
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
          .map(n -> byName.getIfPresent(n.get()))
          .filter(Objects::nonNull)
          .flatMap(p -> p.getConfig().getAllGroupUUIDs().stream())
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

  static class Loader extends CacheLoader<String, ProjectState> {
    private final ProjectState.Factory projectStateFactory;
    private final GitRepositoryManager mgr;
    private final ProjectCacheClock clock;
    private final ProjectConfig.Factory projectConfigFactory;

    @Inject
    Loader(
        ProjectState.Factory psf,
        GitRepositoryManager g,
        ProjectCacheClock clock,
        ProjectConfig.Factory projectConfigFactory) {
      projectStateFactory = psf;
      mgr = g;
      this.clock = clock;
      this.projectConfigFactory = projectConfigFactory;
    }

    @Override
    public ProjectState load(String projectName) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading project", Metadata.builder().projectName(projectName).build())) {
        long now = clock.read();
        Project.NameKey key = Project.nameKey(projectName);
        try (Repository git = mgr.openRepository(key)) {
          ProjectConfig cfg = projectConfigFactory.create(key);
          cfg.load(key, git);

          ProjectState state = projectStateFactory.create(cfg);
          state.initLastCheck(now);
          return state;
        }
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
