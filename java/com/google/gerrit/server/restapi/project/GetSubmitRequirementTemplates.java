// Copyright (C) 2025 The Android Open Source Project
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
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/**
 * REST endpoint to retrieve admin-configured submit requirement templates from {@code All-Projects}.
 *
 * <p>Templates are stored as {@code [submit-requirement-template "name"]} sections in {@code
 * All-Projects/project.config} and are intended to provide project owners with a curated list of
 * common submit requirements to choose from when configuring their projects.
 *
 * <p>Mapped to {@code GET /projects/{project}/submit_requirements:templates}.
 */
@Singleton
public class GetSubmitRequirementTemplates implements RestReadView<ProjectResource> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final AllProjectsName allProjectsName;
  private final GitRepositoryManager repoManager;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  public GetSubmitRequirementTemplates(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      AllProjectsName allProjectsName,
      GitRepositoryManager repoManager,
      ProjectConfig.Factory projectConfigFactory) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.allProjectsName = allProjectsName;
    this.repoManager = repoManager;
    this.projectConfigFactory = projectConfigFactory;
  }

  @Override
  public Response<List<SubmitRequirementInfo>> apply(ProjectResource rsrc)
      throws AuthException, PermissionBackendException, IOException, ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend.currentUser().project(rsrc.getNameKey()).check(ProjectPermission.READ_CONFIG);

    try (Repository repo = repoManager.openRepository(allProjectsName)) {
      ProjectConfig allProjectsConfig = projectConfigFactory.create(allProjectsName);
      allProjectsConfig.load(repo);

      ImmutableList<SubmitRequirementInfo> templates =
          allProjectsConfig.getSubmitRequirementTemplateSections().values().stream()
              .map(sr -> SubmitRequirementJson.format(allProjectsName, sr))
              .collect(ImmutableList.toImmutableList());

      return Response.ok(templates);
    }
  }
}
