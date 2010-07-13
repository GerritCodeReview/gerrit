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

import com.google.gerrit.reviewdb.NewRefRight;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SubmitLabel;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCacheImpl implements ProjectCache {
  private static final String CACHE_NAME = "projects";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Project.NameKey, ProjectState>> type =
            new TypeLiteral<Cache<Project.NameKey, ProjectState>>() {};
        core(type, CACHE_NAME);
        bind(ProjectCacheImpl.class);
        bind(ProjectCache.class).to(ProjectCacheImpl.class);
      }
    };
  }

  private final ProjectState.Factory projectStateFactory;
  private final SchemaFactory<ReviewDb> schema;
  private final SelfPopulatingCache<Project.NameKey, ProjectState> byName;

  @Inject
  ProjectCacheImpl(final ProjectState.Factory psf,
      final SchemaFactory<ReviewDb> sf,
      @WildProjectName final Project.NameKey wp,
      @Named(CACHE_NAME) final Cache<Project.NameKey, ProjectState> byName) {
    projectStateFactory = psf;
    schema = sf;

    this.byName =
        new SelfPopulatingCache<Project.NameKey, ProjectState>(byName) {
          @Override
          public ProjectState createEntry(final Project.NameKey key)
              throws Exception {
            return lookup(key);
          }
        };
  }

  /**
   * Lookup for a state of a specified project on database
   *
   * @param key the project name key
   * @return the project state
   * @throws OrmException
   */
  private ProjectState lookup(final Project.NameKey key) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final Project p = db.projects().get(key);
      if (p == null) {
        return null;
      }

      final Collection<RefRight> rights =
          Collections.unmodifiableCollection(db.refRights().byProject(
              p.getNameKey()).toList());

      final Collection<NewRefRight> newRights =
          Collections.unmodifiableCollection(db.newRefRights().byProject(
              p.getNameKey()).toList());

      final Map<NewRefRight.Id, List<SubmitLabel>> submitLabels =
          new HashMap<NewRefRight.Id, List<SubmitLabel>>();
      for (NewRefRight nrr : newRights) {
        submitLabels.put(nrr.getId(), db.submitLabels().byNewRefRight(
            nrr.getId()).toList());
      }

      return projectStateFactory.create(p, rights, newRights, submitLabels);
    } finally {
      db.close();
    }
  }

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @return the cached data; null if no such project exists.
   */
  public ProjectState get(final Project.NameKey projectName) {
    return byName.get(projectName);
  }

  /** Invalidate the cached information about the given project. */
  public void evict(final Project p) {
    if (p != null) {
      byName.remove(p.getNameKey());
    }
  }

  /** Invalidate the cached information about all projects. */
  public void evictAll() {
    byName.removeAll();
  }
}
