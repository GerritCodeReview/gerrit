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

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCache {
  private static final Logger log = LoggerFactory.getLogger(ProjectCache.class);

  final AnonymousUser anonymousUser;
  private final SchemaFactory<ReviewDb> schema;
  private final Project.NameKey wildProject;
  private final Cache raw;
  private final SelfPopulatingCache auto;

  @Inject
  ProjectCache(final AnonymousUser au, final SchemaFactory<ReviewDb> sf,
      @WildProjectName final Project.NameKey wp, final CacheManager mgr) {
    anonymousUser = au;
    schema = sf;
    wildProject = wp;

    raw = mgr.getCache("projects");
    auto = new SelfPopulatingCache(raw, new CacheEntryFactory() {
      @Override
      public Object createEntry(final Object key) throws Exception {
        return lookup((Project.NameKey) key);
      }
    });
    mgr.replaceCacheWithDecoratedCache(raw, auto);
  }

  private ProjectState lookup(final Project.NameKey key) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final Project p = db.projects().get(key);
      return p != null ? new ProjectState(this, db, p) : null;
    } finally {
      db.close();
    }
  }

  /** Get the rights which are applied to all projects in the system. */
  public Collection<ProjectRight> getWildcardRights() {
    return get(wildProject).getRights();
  }

  /** Invalidate the cached information about the given project. */
  public void evict(final Project p) {
    if (p != null) {
      auto.remove(p.getNameKey());
    }
  }

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @return the cached data; null if no such project exists.
   */
  public ProjectState get(final Project.NameKey projectName) {
    return get0(projectName);
  }

  private ProjectState get0(final Object key) {
    if (key == null) {
      return null;
    }

    final Element m;
    try {
      m = auto.get(key);
    } catch (IllegalStateException e) {
      log.error("Cannot lookup project " + key, e);
      return null;
    } catch (CacheException e) {
      log.error("Cannot lookup project " + key, e);
      return null;
    }

    if (m == null || m.getObjectValue() == null) {
      return null;
    }
    return (ProjectState) m.getObjectValue();
  }
}
