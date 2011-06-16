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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCacheImpl implements ProjectCache {
  private static final String CACHE_NAME = "projects";
  private static final String CACHE_LIST = "project_list";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Project.NameKey, ProjectState>> nameType =
            new TypeLiteral<Cache<Project.NameKey, ProjectState>>() {};
        core(nameType, CACHE_NAME).populateWith(Loader.class);

        final TypeLiteral<Cache<ListKey, SortedSet<Project.NameKey>>> listType =
            new TypeLiteral<Cache<ListKey, SortedSet<Project.NameKey>>>() {};
        core(listType, CACHE_LIST).populateWith(Lister.class);

        bind(ProjectCacheImpl.class);
        bind(ProjectCache.class).to(ProjectCacheImpl.class);
      }
    };
  }

  private final Cache<Project.NameKey, ProjectState> byName;
  private final Cache<ListKey,SortedSet<Project.NameKey>> list;
  private final Lock listLock;
  private volatile long generation;

  @Inject
  ProjectCacheImpl(
      @Named(CACHE_NAME) final Cache<Project.NameKey, ProjectState> byName,
      @Named(CACHE_LIST) final Cache<ListKey, SortedSet<Project.NameKey>> list,
      @GerritServerConfig final Config serverConfig) {
    this.byName = byName;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);

    long checkFrequencyMillis = TimeUnit.MILLISECONDS.convert(
        ConfigUtil.getTimeUnit(serverConfig,
            "cache", "projects", "checkFrequency",
            5, TimeUnit.MINUTES), TimeUnit.MINUTES);
    if (10 < checkFrequencyMillis) {
      // Start with generation 1 (to avoid magic 0 below).
      generation = 1;
      Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
          // This is not exactly thread-safe, but is OK for our use.
          // The only thread that writes the volatile is this task.
          generation = generation + 1;
        }
      }, checkFrequencyMillis, checkFrequencyMillis, TimeUnit.MILLISECONDS);
    } else {
      // Magic generation 0 triggers ProjectState to always
      // check on each needsRefresh() request we make to it.
      generation = 0;
    }
  }

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @return the cached data; null if no such project exists.
   */
  public ProjectState get(final Project.NameKey projectName) {
    ProjectState state = byName.get(projectName);
    if (state != null && state.needsRefresh(generation)) {
      byName.remove(projectName);
      state = byName.get(projectName);
    }
    return state;
  }

  /** Invalidate the cached information about the given project. */
  public void evict(final Project p) {
    if (p != null) {
      byName.remove(p.getNameKey());
    }
  }

  @Override
  public void onCreateProject(Project.NameKey newProjectName) {
    listLock.lock();
    try {
      SortedSet<Project.NameKey> n = list.get(ListKey.ALL);
      n = new TreeSet<Project.NameKey>(n);
      n.add(newProjectName);
      list.put(ListKey.ALL, Collections.unmodifiableSortedSet(n));
    } finally {
      listLock.unlock();
    }
  }

  @Override
  public Iterable<Project.NameKey> all() {
    return list.get(ListKey.ALL);
  }

  @Override
  public Iterable<Project.NameKey> byName(final String pfx) {
    return new Iterable<Project.NameKey>() {
      @Override
      public Iterator<Project.NameKey> iterator() {
        return new Iterator<Project.NameKey>() {
          private Project.NameKey next;
          private Iterator<Project.NameKey> itr =
              list.get(ListKey.ALL).tailSet(new Project.NameKey(pfx)).iterator();

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

  static class Loader extends EntryCreator<Project.NameKey, ProjectState> {
    private final ProjectState.Factory projectStateFactory;
    private final GitRepositoryManager mgr;

    @Inject
    Loader(ProjectState.Factory psf, GitRepositoryManager g) {
      projectStateFactory = psf;
      mgr = g;
    }

    @Override
    public ProjectState createEntry(Project.NameKey key) throws Exception {
      try {
        Repository git = mgr.openRepository(key);
        try {
          final ProjectConfig cfg = new ProjectConfig(key);
          cfg.load(git);
          return projectStateFactory.create(cfg);
        } finally {
          git.close();
        }

      } catch (RepositoryNotFoundException notFound) {
        return null;
      }
    }
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  static class Lister extends EntryCreator<ListKey, SortedSet<Project.NameKey>> {
    private final GitRepositoryManager mgr;

    @Inject
    Lister(GitRepositoryManager mgr) {
      this.mgr = mgr;
    }

    @Override
    public SortedSet<Project.NameKey> createEntry(ListKey key) throws Exception {
      return mgr.list();
    }
  }
}
