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

import static com.google.gerrit.server.git.GitRepositoryManager.REFS_DASHBOARDS;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class GetDashboard implements RestReadView<DashboardResource> {
  private final DashboardsCollection dashboards;

  @Option(name = "--inherited", usage = "include inherited dashboards")
  private boolean inherited;

  @Inject
  public GetDashboard(DashboardsCollection dashboards) {
    this.dashboards = dashboards;
  }

  @Override
  public DashboardInfo apply(DashboardResource resource)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    Project.DashboardType type = resource.getType();
    if (inherited && type == null) {
      // inherited flag can only be used with types.
      throw new ResourceNotFoundException("inherited");
    }

    String project = resource.getControl().getProject().getName();
    if (type != null) {
      // The type is not resolved to a definition yet.
      resource = typeOf(resource.getControl(), type);
    }

    return DashboardsCollection.parse(
        resource.getControl().getProject(),
        resource.getRefName().substring(REFS_DASHBOARDS.length()),
        resource.getPathName(),
        resource.getConfig(),
        project,
        Collections.singleton(type));
  }

  private DashboardResource typeOf(ProjectControl ctl, Project.DashboardType type)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    String id = ctl.getProject().getLocalDashboard(type);
    if (Strings.isNullOrEmpty(id)) {
      id = ctl.getProject().getDashboard(type);
    }
    type = Project.DashboardType.fromId(id);
    if (type != null) {
      throw new ResourceNotFoundException();
    } else if (!Strings.isNullOrEmpty(id)) {
      return dashboards.parse(new ProjectResource(ctl), id);
    } else if (!inherited) {
      throw new ResourceNotFoundException();
    }

    Set<Project.NameKey> seen = Sets.newHashSet();
    seen.add(ctl.getProject().getNameKey());
    ProjectState ps = ctl.getProjectState().getParentState();
    while (ps != null && seen.add(ps.getProject().getNameKey())) {
      id = ps.getProject().getDashboard(type);
      type = Project.DashboardType.fromId(id);
      if (type != null) {
        throw new ResourceNotFoundException();
      } else if (!Strings.isNullOrEmpty(id)) {
        ctl = ps.controlFor(ctl.getCurrentUser());
        return dashboards.parse(new ProjectResource(ctl), id);
      }
      ps = ps.getParentState();
    }
    throw new ResourceNotFoundException();
  }
}
