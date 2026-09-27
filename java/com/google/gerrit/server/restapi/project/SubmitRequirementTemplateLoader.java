// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementTemplateResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

@Singleton
class SubmitRequirementTemplateLoader {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  SubmitRequirementTemplateLoader(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      ProjectConfig.Factory projectConfigFactory) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.projectConfigFactory = projectConfigFactory;
  }

  LinkedHashMap<String, SubmitRequirementTemplateResource> load(ProjectResource project)
      throws AuthException, PermissionBackendException, IOException, ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    LinkedHashMap<String, SubmitRequirementTemplateResource> templates = new LinkedHashMap<>();
    ProjectState currentProjectState = project.getProjectState();
    for (ProjectState projectState : project.getProjectState().treeInOrder()) {
      try {
        checkCanReadConfig(projectState);
      } catch (AuthException e) {
        if (projectState.getNameKey().equals(currentProjectState.getNameKey())) {
          throw e;
        }
        continue;
      }

      for (SubmitRequirement submitRequirement : listTemplates(projectState)) {
        String lowerName = submitRequirement.name().toLowerCase(Locale.US);
        SubmitRequirementTemplateResource old = templates.get(lowerName);
        if (old == null || old.getSubmitRequirementTemplate().allowOverrideInChildProjects()) {
          templates.put(
              lowerName,
              new SubmitRequirementTemplateResource(
                  project, projectState.getNameKey(), submitRequirement));
        }
      }
    }
    return templates;
  }

  private void checkCanReadConfig(ProjectState projectState)
      throws AuthException, PermissionBackendException {
    try {
      permissionBackend
          .currentUser()
          .project(projectState.getNameKey())
          .check(ProjectPermission.READ_CONFIG);
    } catch (AuthException e) {
      throw new AuthException(projectState.getNameKey() + ": " + e.getMessage(), e);
    }
  }

  private Iterable<SubmitRequirement> listTemplates(ProjectState projectState)
      throws IOException, ConfigInvalidException {
    ProjectConfig projectConfig = projectConfigFactory.create(projectState.getNameKey());
    try (Repository repo = repoManager.openRepository(projectState.getNameKey())) {
      projectConfig.load(repo);
    }
    return projectConfig.getSubmitRequirementTemplateSections().values();
  }
}
