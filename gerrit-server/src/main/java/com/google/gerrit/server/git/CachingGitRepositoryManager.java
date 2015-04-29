package com.google.gerrit.server.git;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class CachingGitRepositoryManager implements GitRepositoryManager {
  private static final Logger log = LoggerFactory
      .getLogger(CachingGitRepositoryManager.class);

  protected static final String CACHE_LIST = "project_list";

  protected static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  protected class Lister extends
      CacheLoader<ListKey, SortedSet<Project.NameKey>> {

    @Override
    public SortedSet<Project.NameKey> load(ListKey key) throws Exception {
      return scan();
    }
  }

  protected final Lock namesUpdateLock;
  protected final LoadingCache<ListKey, SortedSet<Project.NameKey>> list;

  CachingGitRepositoryManager(LoadingCache<ListKey, SortedSet<Project.NameKey>> list) {
    namesUpdateLock = new ReentrantLock(true /* fair */);
    this.list = list;
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
}
