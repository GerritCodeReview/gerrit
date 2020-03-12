// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CreateGroupPermissionSyncer;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * This class adds the "revert" permission to all hosts that call this method with the relevant
 * projectName. This class should be called with AllProjects as the project, by all hosts before
 * enabling the "revert" permission.
 */
public class GrantRevertPermission {

  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;
  private final CreateGroupPermissionSyncer createGroupPermissionSyncer;

  @Inject
  public GrantRevertPermission(
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache,
      CreateGroupPermissionSyncer createGroupPermissionSyncer) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
    this.createGroupPermissionSyncer = createGroupPermissionSyncer;
  }

  public void execute(Project.NameKey projectName)
      throws IOException, ConfigInvalidException, AuthException, BadRequestException,
          PermissionBackendException, ResourceConflictException {
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName)) {
      ProjectConfig projectConfig = projectConfigFactory.read(md);

      AccessSection section = new AccessSection("refs/heads/*");
      Permission permission = new Permission("revert");
      PermissionRule permissionRule =
          new PermissionRule(
              new GroupReference(AccountGroup.uuid("global:Registered-Users"), "Registered-Users"));
      permission.add(permissionRule);
      section.addPermission(permission);
      AddAccessSection(projectConfig, section);

      md.setMessage("Add revert permission to all registered users\n");

      projectConfig.commit(md);
      projectCache.evict(projectConfig.getProject());
      createGroupPermissionSyncer.syncIfNeeded();
    }
  }

  private void AddAccessSection(ProjectConfig projectConfig, AccessSection sectionToAdd) {
    AccessSection currentSection = projectConfig.getAccessSection(sectionToAdd.getName());

    if (currentSection == null) {
      // Add AccessSection
      projectConfig.replace(sectionToAdd);
    } else {
      currentSection.addPermission(sectionToAdd.getPermission("revert"));
    }
  }
}
