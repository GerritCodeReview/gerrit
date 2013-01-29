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

package com.google.gerrit.server.project;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.gerrit.server.project.SetDashboard.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Option;

class SetDefaultDashboard implements RestModifyView<DashboardResource, Input> {
  private final ProjectCache cache;
  private final MetaDataUpdate.Server updateFactory;
  private final DashboardsCollection dashboards;
  private final Provider<GetDashboard> get;

  @Option(name = "--inherited", usage = "set dashboard inherited by children")
  private boolean inherited;

  @Inject
  SetDefaultDashboard(ProjectCache cache,
      MetaDataUpdate.Server updateFactory,
      DashboardsCollection dashboards,
      Provider<GetDashboard> get) {
    this.cache = cache;
    this.updateFactory = updateFactory;
    this.dashboards = dashboards;
    this.get = get;
  }

  @Override
  public Object apply(DashboardResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    if (input == null) {
      input = new Input(); // Delete would set input to null.
    }
    input.id = Strings.emptyToNull(input.id);

    ProjectControl ctl = resource.getControl();
    IdentifiedUser user = (IdentifiedUser) ctl.getCurrentUser();
    if (!ctl.isOwner()) {
      throw new AuthException("not project owner");
    }

    DashboardResource target = null;
    if (input.id != null) {
      try {
        target = dashboards.parse(
            new ProjectResource(ctl),
            IdString.fromUrl(input.id));
      } catch (ResourceNotFoundException e) {
        throw new BadRequestException("dashboard " + input.id + " not found");
      }
    }

    try {
      MetaDataUpdate md = updateFactory.create(ctl.getProject().getNameKey());
      try {
        ProjectConfig config = ProjectConfig.read(md);
        Project project = config.getProject();
        if (inherited) {
          project.setDefaultDashboard(input.id);
        } else {
          project.setLocalDefaultDashboard(input.id);
        }

        String msg = Objects.firstNonNull(
          Strings.emptyToNull(input.commitMessage),
          input.id == null
            ? "Removed default dashboard.\n"
            : String.format("Changed default dashboard to %s.\n", input.id));
        if (!msg.endsWith("\n")) {
          msg += "\n";
        }
        md.setAuthor(user);
        md.setMessage(msg);
        config.commit(md);
        cache.evict(ctl.getProject());

        if (target != null) {
          DashboardInfo info = get.get().apply(target);
          info.isDefault = true;
          return info;
        }
        return Response.none();
      } finally {
        md.close();
      }
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(ctl.getProject().getName());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(String.format(
          "invalid project.config: %s", e.getMessage()));
    }
  }

  static class CreateDefault implements
      RestModifyView<ProjectResource, SetDashboard.Input> {
    private final Provider<SetDefaultDashboard> setDefault;

    @Option(name = "--inherited", usage = "set dashboard inherited by children")
    private boolean inherited;

    @Inject
    CreateDefault(Provider<SetDefaultDashboard> setDefault) {
      this.setDefault = setDefault;
    }

    @Override
    public Object apply(ProjectResource resource, Input input)
        throws AuthException, BadRequestException, ResourceConflictException,
        Exception {
      SetDefaultDashboard set = setDefault.get();
      set.inherited = inherited;
      return Response.created(set.apply(
          DashboardResource.projectDefault(resource.getControl()),
          input));
    }
  }
}
