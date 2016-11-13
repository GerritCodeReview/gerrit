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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_DASHBOARDS;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.project.DashboardsCollection.DashboardInfo;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

class GetDashboard implements RestReadView<DashboardResource> {
  private final DashboardsCollection dashboards;

  @Option(name = "--inherited", usage = "include inherited dashboards")
  private boolean inherited;

  @Inject
  GetDashboard(DashboardsCollection dashboards) {
    this.dashboards = dashboards;
  }

  @Override
  public DashboardInfo apply(DashboardResource resource)
      throws ResourceNotFoundException, ResourceConflictException, IOException {
    if (inherited && !resource.isProjectDefault()) {
      // inherited flag can only be used with default.
      throw new ResourceNotFoundException("inherited");
    }

    String project = resource.getControl().getProject().getName();
    if (resource.isProjectDefault()) {
      // The default is not resolved to a definition yet.
      try {
        resource = defaultOf(resource.getControl());
      } catch (ConfigInvalidException e) {
        throw new ResourceConflictException(e.getMessage());
      }
    }

    return DashboardsCollection.parse(
        resource.getControl().getProject(),
        resource.getRefName().substring(REFS_DASHBOARDS.length()),
        resource.getPathName(),
        resource.getConfig(),
        project,
        true);
  }

  private DashboardResource defaultOf(ProjectControl ctl)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    String id = ctl.getProject().getLocalDefaultDashboard();
    if (Strings.isNullOrEmpty(id)) {
      id = ctl.getProject().getDefaultDashboard();
    }
    if ("default".equals(id)) {
      throw new ResourceNotFoundException();
    } else if (!Strings.isNullOrEmpty(id)) {
      return parse(ctl, id);
    } else if (!inherited) {
      throw new ResourceNotFoundException();
    }

    for (ProjectState ps : ctl.getProjectState().tree()) {
      id = ps.getProject().getDefaultDashboard();
      if ("default".equals(id)) {
        throw new ResourceNotFoundException();
      } else if (!Strings.isNullOrEmpty(id)) {
        ctl = ps.controlFor(ctl.getUser());
        return parse(ctl, id);
      }
    }
    throw new ResourceNotFoundException();
  }

  private DashboardResource parse(ProjectControl ctl, String id)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    List<String> p = Lists.newArrayList(Splitter.on(':').limit(2).split(id));
    String ref = Url.encode(p.get(0));
    String path = Url.encode(p.get(1));
    return dashboards.parse(new ProjectResource(ctl), IdString.fromUrl(ref + ':' + path));
  }
}
