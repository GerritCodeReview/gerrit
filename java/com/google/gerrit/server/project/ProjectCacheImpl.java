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

import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCacheImpl implements ProjectCache {
  private static final Logger log = LoggerFactory.getLogger(ProjectCacheImpl.class);

  private static final String CACHE_NAME = "projects";
  private static final String CACHE_LIST = "project_list";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        configureProjectStateCache();

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

      @SuppressWarnings("deprecation")
      private void configureProjectStateCache() {
        cache(CACHE_NAME, String.class, ProjectState.class)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .refreshAfterWriteConfigName("checkFrequency")
            .loader(Loader.class);
      }
    };
  }

  private final AllProjectsName allProjectsName;
  private final AllUsersName allUsersName;
  private final LoadingCache<String, ProjectState> byName;
  private final LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list;
  private final Lock listLock;
  private final Provider<ProjectIndexer> indexer;

  @Inject
  ProjectCacheImpl(
      final AllProjectsName allProjectsName,
      final AllUsersName allUsersName,
      @Named(CACHE_NAME) LoadingCache<String, ProjectState> byName,
      @Named(CACHE_LIST) LoadingCache<ListKey, ImmutableSortedSet<Project.NameKey>> list,
      Provider<ProjectIndexer> indexer) {
    this.allProjectsName = allProjectsName;
    this.allUsersName = allUsersName;
    this.byName = byName;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);
    this.indexer = indexer;
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
      log.warn("Cannot read project " + projectName, e);
      return null;
    }
  }

  @Override
  public ProjectState checkedGet(Project.NameKey projectName) throws IOException {
    if (projectName == null) {
      return null;
    }
    try {
      return byName.get(projectName.get());
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof RepositoryNotFoundException)) {
        log.warn(String.format("Cannot read project %s", projectName.get()), e);
        Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
        throw new IOException(e);
      }
      log.debug("Cannot find project {}", projectName.get(), e);
      return null;
    }
  }

  @Override
  public void evict(Project p) throws IOException {
    evict(p.getNameKey());
  }

  @Override
  public void evict(Project.NameKey p) throws IOException {
    if (p != null) {
      byName.invalidate(p.get());
      // We could call byName.refresh here to start reloading the element in the background, but
      // there's no point, since we immediately reload the value in this thread.
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
      log.warn("Cannot list available projects", e);
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
      log.warn("Cannot list available projects", e);
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
      log.warn("Cannot list available projects", e);
      return ImmutableSortedSet.of();
    }
  }

  @Override
  public Set<AccountGroup.UUID> guessRelevantGroupUUIDs() {
    return all()
        .stream()
        .map(n -> byName.getIfPresent(n.get()))
        .filter(Objects::nonNull)
        .flatMap(p -> p.getConfig().getAllGroupUUIDs().stream())
        // getAllGroupUUIDs shouldn't really return null UUIDs, but harden
        // against them just in case there is a bug or corner case.
        .filter(id -> id != null && id.get() != null)
        .collect(toSet());
  }

  @Override
  public ImmutableSortedSet<Project.NameKey> byName(String pfx) {
    Project.NameKey start = new Project.NameKey(pfx);
    Project.NameKey end = new Project.NameKey(pfx + Character.MAX_VALUE);
    try {
      // Right endpoint is exclusive, but U+FFFF is a non-character so no project ends with it.
      return list.get(ListKey.ALL).subSet(start, end);
    } catch (ExecutionException e) {
      log.warn("Cannot look up projects for prefix " + pfx, e);
      return ImmutableSortedSet.of();
    }
  }

  static class Loader extends CacheLoader<String, ProjectState> {
    private final ProjectState.Factory projectStateFactory;
    private final GitRepositoryManager mgr;

    @Inject
    Loader(ProjectState.Factory psf, GitRepositoryManager g) {
      projectStateFactory = psf;
      mgr = g;
    }

    @Override
    public ProjectState load(String projectName) throws Exception {
      Project.NameKey key = new Project.NameKey(projectName);
      try (Repository git = mgr.openRepository(key)) {
        ProjectConfig cfg = new ProjectConfig(key);
        cfg.load(git);
        return projectStateFactory.create(cfg);
      }
    }

    @Override
    public ListenableFuture<ProjectState> reload(String projectName, ProjectState oldState) {
      ObjectId oldId = oldState.getConfigRevision();
      Project.NameKey key = new Project.NameKey(projectName);
      // Check the ref synchronously in this thread, even though CacheBuilder recommends using an
      // async implementation. In the common case, the ref check is fast and we don't have to
      // re-parse the state.
      try (Repository git = mgr.openRepository(key)) {
        Ref ref = git.getRefDatabase().exactRef(RefNames.REFS_CONFIG);
        ObjectId newId = ref != null ? ref.getObjectId() : null;
        if (Objects.equals(oldId, newId)) {
          return Futures.immediateFuture(oldState);
        }
        ProjectConfig cfg = new ProjectConfig(key);
        cfg.load(git, newId);
        return Futures.immediateFuture(projectStateFactory.create(cfg));
      } catch (ConfigInvalidException | IOException e) {
        return Futures.immediateFailedFuture(e);
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
      return ImmutableSortedSet.copyOf(mgr.list());
    }
  }
}
