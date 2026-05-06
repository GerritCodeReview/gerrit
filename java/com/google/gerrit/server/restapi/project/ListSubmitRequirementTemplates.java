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
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.gerrit.server.project.SubmitRequirementTemplateResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * A rest reads submit requirement template stored in project.config for current project and its
 * parents.
 */
@Singleton
public class ListSubmitRequirementTemplates implements RestReadView<ProjectResource> {
  private final SubmitRequirementTemplateLoader templateLoader;

  @Inject
  public ListSubmitRequirementTemplates(SubmitRequirementTemplateLoader templateLoader) {
    this.templateLoader = templateLoader;
  }

  @Override
  public Response<List<SubmitRequirementInfo>> apply(ProjectResource rsrc)
      throws AuthException, PermissionBackendException, IOException, ConfigInvalidException {
    return Response.ok(
        templateLoader.load(rsrc).values().stream()
            .map(this::format)
            .collect(ImmutableList.toImmutableList()));
  }

  private SubmitRequirementInfo format(SubmitRequirementTemplateResource resource) {
    return SubmitRequirementJson.format(
        resource.getSourceProject(), resource.getSubmitRequirementTemplate());
  }
}
