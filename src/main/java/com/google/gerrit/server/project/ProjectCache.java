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

/** Cache of project information, including access rights. */
@Singleton
public class ProjectCache {
  private static final String CACHE_NAME = "projects";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Project.NameKey, ProjectState>> type =
            new TypeLiteral<Cache<Project.NameKey, ProjectState>>() {};
        core(type, CACHE_NAME);
        bind(ProjectCache.class);
      }
    };
  }

  final AnonymousUser anonymousUser;
  final Project.NameKey wildProject;
  private final SchemaFactory<ReviewDb> schema;
  private final SelfPopulatingCache<Project.NameKey, ProjectState> byName;

  @Inject
  ProjectCache(final AnonymousUser au, final SchemaFactory<ReviewDb> sf,
      @WildProjectName final Project.NameKey wp,
      @Named(CACHE_NAME) final Cache<Project.NameKey, ProjectState> byName) {
    anonymousUser = au;
    schema = sf;
    wildProject = wp;

    this.byName =
        new SelfPopulatingCache<Project.NameKey, ProjectState>(byName) {
          @Override
          public ProjectState createEntry(final Project.NameKey key)
              throws Exception {
            return lookup(key);
          }
        };
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
  Collection<ProjectRight> getWildcardRights() {
    return get(wildProject).getLocalRights();
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
}
