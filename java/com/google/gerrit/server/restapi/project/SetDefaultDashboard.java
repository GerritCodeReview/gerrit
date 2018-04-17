// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.common.SetDashboardInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.DashboardResource;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Option;

class SetDefaultDashboard implements RestModifyView<DashboardResource, SetDashboardInput> {
  private final ProjectCache cache;
  private final MetaDataUpdate.Server updateFactory;
  private final DashboardsCollection dashboards;
  private final Provider<GetDashboard> get;
  private final PermissionBackend permissionBackend;

  @Option(name = "--inherited", usage = "set dashboard inherited by children")
  private boolean inherited;

  @Inject
  SetDefaultDashboard(
      ProjectCache cache,
      MetaDataUpdate.Server updateFactory,
      DashboardsCollection dashboards,
      Provider<GetDashboard> get,
      PermissionBackend permissionBackend) {
    this.cache = cache;
    this.updateFactory = updateFactory;
    this.dashboards = dashboards;
    this.get = get;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<DashboardInfo> apply(DashboardResource rsrc, SetDashboardInput input)
      throws RestApiException, IOException, PermissionBackendException {
    if (input == null) {
      input = new SetDashboardInput(); // Delete would set input to null.
    }
    input.id = Strings.emptyToNull(input.id);

    permissionBackend
        .user(rsrc.getUser())
        .project(rsrc.getProjectState().getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    DashboardResource target = null;
    if (input.id != null) {
      try {
        target =
            dashboards.parse(
                new ProjectResource(rsrc.getProjectAccessor(), rsrc.getUser()),
                IdString.fromUrl(input.id));
      } catch (ResourceNotFoundException e) {
        throw new BadRequestException("dashboard " + input.id + " not found");
      } catch (ConfigInvalidException e) {
        throw new ResourceConflictException(e.getMessage());
      }
    }

    try (MetaDataUpdate md = updateFactory.create(rsrc.getProjectState().getNameKey())) {
      ProjectConfig config = ProjectConfig.read(md);
      Project project = config.getProject();
      if (inherited) {
        project.setDefaultDashboard(input.id);
      } else {
        project.setLocalDefaultDashboard(input.id);
      }

      String msg =
          MoreObjects.firstNonNull(
              Strings.emptyToNull(input.commitMessage),
              input.id == null
                  ? "Removed default dashboard.\n"
                  : String.format("Changed default dashboard to %s.\n", input.id));
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      md.setAuthor(rsrc.getUser().asIdentifiedUser());
      md.setMessage(msg);
      config.commit(md);
      cache.evict(rsrc.getProjectState().getProject());

      if (target != null) {
        DashboardInfo info = get.get().apply(target);
        info.isDefault = true;
        return Response.ok(info);
      }
      return Response.none();
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(rsrc.getProjectState().getProject().getName());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("invalid project.config: %s", e.getMessage()));
    }
  }

  static class CreateDefault implements RestModifyView<ProjectResource, SetDashboardInput> {
    private final Provider<SetDefaultDashboard> setDefault;

    @Option(name = "--inherited", usage = "set dashboard inherited by children")
    private boolean inherited;

    @Inject
    CreateDefault(Provider<SetDefaultDashboard> setDefault) {
      this.setDefault = setDefault;
    }

    @Override
    public Response<DashboardInfo> apply(ProjectResource resource, SetDashboardInput input)
        throws RestApiException, IOException, PermissionBackendException {
      SetDefaultDashboard set = setDefault.get();
      set.inherited = inherited;
      return set.apply(
          DashboardResource.projectDefault(resource.getProjectAccessor(), resource.getUser()),
          input);
    }
  }
}
