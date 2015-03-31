// Copyright (C) 2015 The Android Open Source Project
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
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@code GitRepositoryManager} caching the list of all projects its
 * repositories it is managing
 */
public abstract class CachingGitRepositoryManager implements
    GitRepositoryManager {
  private static final Logger log = LoggerFactory
      .getLogger(CachingGitRepositoryManager.class);

  protected static final String CACHE_LIST = "project_list";

  protected static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  protected static class Lister extends
      CacheLoader<ListKey, SortedSet<Project.NameKey>> {

    private CachingGitRepositoryManager mgr;

    @Inject
    Lister(CachingGitRepositoryManager mgr) {
      this.mgr = mgr;
    }

    @Override
    public SortedSet<Project.NameKey> load(ListKey key) throws Exception {
      return mgr.scan();
    }
  }

  protected final LoadingCache<ListKey, SortedSet<Project.NameKey>> list;
  protected final Lock namesUpdateLock;

  @Inject
  CachingGitRepositoryManager(
      @Named(CACHE_LIST) LoadingCache<ListKey, SortedSet<Project.NameKey>> list) {
    this.list = list;
    namesUpdateLock = new ReentrantLock(true /* fair */);
  }

  @Override
  public void onCreateProject(final Project.NameKey newProjectName) {
    namesUpdateLock.lock();
    try {
      SortedSet<Project.NameKey> n = Sets.newTreeSet(list.get(ListKey.ALL));
      n.add(newProjectName);
      list.put(ListKey.ALL, Collections.unmodifiableSortedSet(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      namesUpdateLock.unlock();
    }
  }

  @Override
  public void onRemoveProject(final Project p) {
    namesUpdateLock.lock();
    try {
      SortedSet<Project.NameKey> n = Sets.newTreeSet(list.get(ListKey.ALL));
      n.remove(p.getNameKey());
      list.put(ListKey.ALL, Collections.unmodifiableSortedSet(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      namesUpdateLock.unlock();
    }
  }

  @Override
  public SortedSet<Project.NameKey> list() {
    try {
      return list.get(ListKey.ALL);
    } catch (ExecutionException e) {
      log.warn("Cannot list available projects", e);
      return new TreeSet<>(); // TODO something simpler to not instantiate
    }
  }

  /**
   * scan persistence to discover repositories
   * @return names of discovered projects
   */
  protected abstract SortedSet<Project.NameKey> scan();
}
