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

import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCacheImpl implements ProjectCache {
  private static final Logger log = LoggerFactory
      .getLogger(ProjectCacheImpl.class);

  private static final String CACHE_NAME = "projects";
  private static final String CACHE_LIST = "project_list";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, String.class, ProjectState.class)
          .loader(Loader.class);

        cache(CACHE_LIST,
            ListKey.class,
            new TypeLiteral<SortedSet<Project.NameKey>>() {})
          .maximumWeight(1)
          .loader(Lister.class);

        bind(ProjectCacheImpl.class);
        bind(ProjectCache.class).to(ProjectCacheImpl.class);
      }
    };
  }

  private final AllProjectsName allProjectsName;
  private final LoadingCache<String, ProjectState> byName;
  private final LoadingCache<ListKey, SortedSet<Project.NameKey>> list;
  private final Lock listLock;
  private final ProjectCacheClock clock;

  @Inject
  ProjectCacheImpl(
      final AllProjectsName allProjectsName,
      @Named(CACHE_NAME) LoadingCache<String, ProjectState> byName,
      @Named(CACHE_LIST) LoadingCache<ListKey, SortedSet<Project.NameKey>> list,
      ProjectCacheClock clock) {
    this.allProjectsName = allProjectsName;
    this.byName = byName;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);
    this.clock = clock;
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
  public ProjectState get(final Project.NameKey projectName) {
     try {
      return checkedGet(projectName);
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public ProjectState checkedGet(Project.NameKey projectName)
      throws IOException {
    if (projectName == null) {
      return null;
    }
    try {
      ProjectState state = byName.get(projectName.get());
      if (state != null && state.needsRefresh(clock.read())) {
        byName.invalidate(projectName.get());
        state = byName.get(projectName.get());
      }
      return state;
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof RepositoryNotFoundException)) {
        log.warn(String.format("Cannot read project %s", projectName.get()), e);
        Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
        throw new IOException(e);
      }
      return null;
    }
  }

  @Override
  public void evict(final Project p) {
    if (p != null) {
      byName.invalidate(p.getNameKey().get());
    }
  }

  @Override
  public void remove(final Project p) {
    listLock.lock();
    try {
      SortedSet<Project.NameKey> n = Sets.newTreeSet(list.get(ListKey.ALL));
      n.remove(p.getNameKey());
      list.put(ListKey.ALL, Collections.unmodifiableSortedSet(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      listLock.unlock();
    }
    evict(p);
  }

  @Override
  public void onCreateProject(Project.NameKey newProjectName) {
    listLock.lock();
    try {
      SortedSet<Project.NameKey> n = Sets.newTreeSet(list.get(ListKey.ALL));
      n.add(newProjectName);
      list.put(ListKey.ALL, Collections.unmodifiableSortedSet(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      listLock.unlock();
    }
  }

  @Override
  public Iterable<Project.NameKey> all() {
    try {
      return list.get(ListKey.ALL);
    } catch (ExecutionException e) {
      log.warn("Cannot list available projects", e);
      return Collections.emptyList();
    }
  }

  @Override
  public Set<AccountGroup.UUID> guessRelevantGroupUUIDs() {
    Set<AccountGroup.UUID> groups = Sets.newHashSet();
    for (Project.NameKey n : all()) {
      ProjectState p = byName.getIfPresent(n.get());
      if (p != null) {
        groups.addAll(p.getConfig().getAllGroupUUIDs());
      }
    }
    return groups;
  }

  @Override
  public Iterable<Project.NameKey> byName(final String pfx) {
    final Iterable<Project.NameKey> src;
    try {
      src = list.get(ListKey.ALL).tailSet(new Project.NameKey(pfx));
    } catch (ExecutionException e) {
      return Collections.emptyList();
    }
    return new Iterable<Project.NameKey>() {
      @Override
      public Iterator<Project.NameKey> iterator() {
        return new Iterator<Project.NameKey>() {
          private Iterator<Project.NameKey> itr = src.iterator();
          private Project.NameKey next;

          @Override
          public boolean hasNext() {
            if (next != null) {
              return true;
            }

            if (!itr.hasNext()) {
              return false;
            }

            Project.NameKey r = itr.next();
            if (r.get().startsWith(pfx)) {
              next = r;
              return true;
            } else {
              itr = Collections.<Project.NameKey> emptyList().iterator();
              return false;
            }
          }

          @Override
          public Project.NameKey next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }

            Project.NameKey r = next;
            next = null;
            return r;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
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
      Repository git = mgr.openRepository(key);
      try {
        ProjectConfig cfg = new ProjectConfig(key);
        cfg.load(git);
        return projectStateFactory.create(cfg);
      } finally {
        git.close();
      }
    }
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  static class Lister extends CacheLoader<ListKey, SortedSet<Project.NameKey>> {
    private final GitRepositoryManager mgr;

    @Inject
    Lister(GitRepositoryManager mgr) {
      this.mgr = mgr;
    }

    @Override
    public SortedSet<Project.NameKey> load(ListKey key) throws Exception {
      return mgr.list();
    }
  }
}
