// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.TypeLiteral;
import org.eclipse.jgit.lib.Config;

public class DashboardResource implements RestResource {
  public static final TypeLiteral<RestView<DashboardResource>> DASHBOARD_KIND =
      new TypeLiteral<RestView<DashboardResource>>() {};

  public static DashboardResource projectDefault(ProjectState projectState, CurrentUser user) {
    return new DashboardResource(projectState, user, null, null, null, true);
  }

  private final ProjectState projectState;
  private final CurrentUser user;
  private final String refName;
  private final String pathName;
  private final Config config;
  private final boolean projectDefault;

  public DashboardResource(
      ProjectState projectState,
      CurrentUser user,
      String refName,
      String pathName,
      Config config,
      boolean projectDefault) {
    this.projectState = projectState;
    this.user = user;
    this.refName = refName;
    this.pathName = pathName;
    this.config = config;
    this.projectDefault = projectDefault;
  }

  public ProjectState getProjectState() {
    return projectState;
  }

  public CurrentUser getUser() {
    return user;
  }

  public String getRefName() {
    return refName;
  }

  public String getPathName() {
    return pathName;
  }

  public Config getConfig() {
    return config;
  }

  public boolean isProjectDefault() {
    return projectDefault;
  }
}
