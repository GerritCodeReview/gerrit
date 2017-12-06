// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.schema.AclUtil.grant;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/** Migrate CREATE_GROUP global capability to CREATE permission on refs/groups/* */
public class Schema_166 extends SchemaVersion {
  private static final String COMMIT_MSG =
      "Migrate CREATE_GROUP global capability to CREATE permission on refs/groups/*";

  private final AllProjectsName allProjects;
  private final AllUsersName allUsers;
  private final ProjectCache projectCache;
  private final Provider<MetaDataUpdate.Server> metaDataUpdateFactory;
  private final GitRepositoryManager repoManager;

  @Inject
  Schema_166(
      Provider<Schema_165> prior,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      ProjectCache projectCache,
      Provider<MetaDataUpdate.Server> metaDataUpdateFactory,
      GitRepositoryManager repoManager) {
    super(prior);
    this.allProjects = allProjects;
    this.allUsers = allUsers;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.repoManager = repoManager;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    try {
      ProjectState allProjectsState = projectCache.checkedGet(allProjects);
      checkNotNull(allProjectsState, "Can't obtain project state for " + allProjects);
      ProjectState allUsersState = projectCache.checkedGet(allUsers);
      checkNotNull(allUsersState, "Can't obtain project state for " + allUsers);

      Set<GroupReference> groupsWithCapability;
      try (Repository repo = repoManager.openRepository(allProjects);
          MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, repo)) {
        ProjectConfig config = ProjectConfig.read(md);
        AccessSection globalCapabilities =
            config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES);
        if (globalCapabilities == null) {
          return;
        }
        Permission perm = globalCapabilities.getPermission(GlobalCapability.CREATE_GROUP);
        if (perm == null || perm.getRules().isEmpty()) {
          return;
        }
        groupsWithCapability =
            perm.getRules().stream().map(p -> p.getGroup()).collect(toImmutableSet());
      }

      if (groupsWithCapability.isEmpty()) {
        // Nothing to do
        return;
      }

      try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsers)) {
        ProjectConfig config = ProjectConfig.read(md);
        AccessSection createGroupAccessSection =
            config.getAccessSection(RefNames.REFS_GROUPS + "*", true);

        Permission existingCreatePermission =
            createGroupAccessSection.getPermission(Permission.CREATE);

        ImmutableSet<GroupReference> existingPermissions =
            existingCreatePermission != null
                ? existingCreatePermission
                    .getRules()
                    .stream()
                    .map(r -> r.getGroup())
                    .collect(toImmutableSet())
                : ImmutableSet.of();

        groupsWithCapability
            .stream()
            .filter(g -> !existingPermissions.contains(g))
            .forEach(
                g -> grant(config, createGroupAccessSection, Permission.CREATE, false, true, g));

        md.setMessage(COMMIT_MSG);
        config.commit(md);
        projectCache.evict(config.getProject());
      }

      // Remove deprecated global capability
      try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allProjects)) {
        ProjectConfig config = ProjectConfig.read(md);
        AccessSection global = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES);
        if (global != null) {
          global.removePermission(GlobalCapability.CREATE_GROUP);
          md.setMessage(COMMIT_MSG);
          config.commit(md);
          projectCache.evict(config.getProject());
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException(e);
    }
  }
}
