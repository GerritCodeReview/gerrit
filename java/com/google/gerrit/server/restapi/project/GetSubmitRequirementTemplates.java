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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * A rest reads submit requirement template stored in project.config for current project and its
 * parents.
 */
@Singleton
public class GetSubmitRequirementTemplates implements RestReadView<ProjectResource> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  public GetSubmitRequirementTemplates(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      ProjectConfig.Factory projectConfigFactory) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.projectConfigFactory = projectConfigFactory;
  }

  @Override
  public Response<List<SubmitRequirementInfo>> apply(ProjectResource rsrc)
      throws AuthException, PermissionBackendException, IOException, ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    LinkedHashMap<String, SubmitRequirementInfo> templates = new LinkedHashMap<>();
    for (ProjectState projectState : rsrc.getProjectState().treeInOrder()) {
      try {
        permissionBackend
            .currentUser()
            .project(projectState.getNameKey())
            .check(ProjectPermission.READ_CONFIG);
      } catch (AuthException e) {
        throw new AuthException(projectState.getNameKey() + ": " + e.getMessage(), e);
      }

      ProjectConfig projectConfig = projectConfigFactory.create(projectState.getNameKey());
      try (Repository repo = repoManager.openRepository(projectState.getNameKey())) {
        projectConfig.load(repo);
      }

      for (SubmitRequirement submitRequirement :
          projectConfig.getSubmitRequirementTemplateSections().values()) {
        String lowerName = submitRequirement.name().toLowerCase(Locale.US);
        SubmitRequirementInfo old = templates.get(lowerName);
        if (old == null || old.allowOverrideInChildProjects) {
          templates.put(
              lowerName,
              SubmitRequirementJson.format(projectState.getNameKey(), submitRequirement));
        }
      }
    }

    return Response.ok(ImmutableList.copyOf(templates.values()));
  }
}
