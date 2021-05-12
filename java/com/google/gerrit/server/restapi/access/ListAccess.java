// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.restapi.access;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.restapi.project.GetAccess;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.kohsuke.args4j.Option;

/**
 * REST endpoint to list members of the {@link AccessCollection}.
 *
 * <p>This REST endpoint handles {@code GET /access/} requests.
 */
public class ListAccess implements RestReadView<TopLevelResource> {

  @Option(
      name = "--project",
      aliases = {"-p"},
      metaVar = "PROJECT",
      usage = "projects for which the access rights should be returned")
  private List<String> projects = new ArrayList<>();

  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final GetAccess getAccess;

  @Inject
  public ListAccess(
      PermissionBackend permissionBackend, ProjectCache projectCache, GetAccess getAccess) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.getAccess = getAccess;
  }

  @Override
  public Response<Map<String, ProjectAccessInfo>> apply(TopLevelResource resource)
      throws Exception {
    Map<String, ProjectAccessInfo> access = new TreeMap<>();
    for (Project.NameKey projectName :
        projects.stream()
            .filter(project -> !Strings.nullToEmpty(project).isEmpty())
            .map(IdString::fromUrl)
            .map(IdString::get)
            .map(String::trim)
            .map(Project::nameKey)
            .collect(toImmutableList())) {
      if (!projectCache.get(projectName).isPresent()) {
        throw new ResourceNotFoundException(projectName.get());
      }

      try {
        permissionBackend.currentUser().project(projectName).check(ProjectPermission.ACCESS);
      } catch (AuthException e) {
        throw new ResourceNotFoundException(projectName.get(), e);
      }

      access.put(projectName.get(), getAccess.apply(projectName));
    }
    return Response.ok(access);
  }
}
