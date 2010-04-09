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
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
  private final Project.NameKey wildProject;
  private final SchemaFactory<ReviewDb> schema;
  private final SelfPopulatingCache<Project.NameKey, ProjectState> byName;

  @Inject
  ProjectCacheImpl(final ProjectState.Factory psf,
      final SchemaFactory<ReviewDb> sf,
      @WildProjectName final Project.NameKey wp,
      @Named(CACHE_NAME) final Cache<Project.NameKey, ProjectState> byName) {
    projectStateFactory = psf;
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

  /**
   * Retrieves the "wildcard" project state ("wildcard" project is the root node
   * on projects hierarchy)
   *
   * @return the "wildcard" project state
   * @throws OrmException
   */
  private ProjectState getWildProjectState() throws OrmException {
    // "wildcard project" should be retrieved from database
    final ReviewDb db = schema.open();
    try {
      final Project project = db.projects().get(wildProject);
      if (project == null) {
        return null;
      }

      final Collection<RefRight> wildProjectRights =
          Collections.unmodifiableCollection(db.refRights().byProject(
              wildProject).toList());

      final ProjectState.InheritedRights wildProjectInheritedRights =
          new ProjectState.InheritedRights() {
            @Override
            public Collection<RefRight> get() {
              // the "wildcard project" has no inherited rights
              // since its has no parent
              return Collections.emptyList();
            }
          };

      return projectStateFactory.create(project, wildProjectRights,
          wildProjectInheritedRights);
    } finally {
      db.close();
    }
  }

  /**
   * Lookup for a state of a specified project on database
   * @param key the project name key
   * @return the project state
   * @throws OrmException
   */
  private ProjectState lookup(final Project.NameKey key) throws OrmException {
    if (key == null) {
      return null;
    }

    // handles the "wildcard" project state (recursion break)
    if (key.equals(wildProject) || key.get() == null || key.get().isEmpty()) {
      return getWildProjectState();
    }

    final ReviewDb db = schema.open();
    try {

      final Project project = db.projects().get(key);
      if (project == null) {
        return null;
      }

      // retrieves the parent project state (recursively)
      ProjectState parentProjectState = null;

      Project.NameKey parentNameKey = project.getParent();
      if (parentNameKey == null || parentNameKey.get() == null || parentNameKey.get().isEmpty()){
        // if the parent project name is not defined, uses the wildcard project
        parentProjectState = getWildProjectState();
      } else {
        // gets parent project state recursively (unless its available on cache)
        parentProjectState = ProjectCacheImpl.this.get(parentNameKey);
        if (parentProjectState == null){
          parentProjectState = lookup(parentNameKey);
        }
      }

      // retrieves the project rights
      final Collection<RefRight> projectRights =
          Collections.unmodifiableCollection(db.refRights().byProject(
              project.getNameKey()).toList());

      // evaluates the project inherited rights from parent project state
      final ProjectState.InheritedRights inheritedRights =
          new InheritedRightsComposer(parentProjectState);

      return projectStateFactory
          .create(project, projectRights, inheritedRights);

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
  public void evict() {
    // evict all projects
    byName.removeAll();
  }

  /**
   * inner class to compose project inherited rights from parent project state
   */
  class InheritedRightsComposer implements ProjectState.InheritedRights {
    Collection<RefRight> rights = new ArrayList<RefRight>();

    /**
     * Composes a project state inherited rights from parent project state
     * @param parentProjectState the parent project state
     */
    public InheritedRightsComposer(ProjectState parentProjectState) {
      // project inherited rights is defined by union of parent project rights
      // and parent project inherited rights
      for (RefRight right : parentProjectState.getLocalRights()) {
        rights.add(right);
      }
      for (RefRight right : parentProjectState.getInheritedRights()) {
        rights.add(right);
      }
    }

    @Override
    public Collection<RefRight> get() {
      return Collections.unmodifiableCollection(rights);
    }
  }
}
