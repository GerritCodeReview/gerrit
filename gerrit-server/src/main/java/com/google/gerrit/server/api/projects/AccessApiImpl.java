// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.api.projects;

import com.google.gerrit.extensions.api.projects.AccessApi;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.GetAccess;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

public class AccessApiImpl implements AccessApi {
  interface Factory {
    AccessApiImpl create(ProjectResource project);
  }

  private final ProjectResource project;
  private final GetAccess getAccess;

  @Inject
  AccessApiImpl(GetAccess getAccess,
    @Assisted ProjectResource project) {
    this.project = project;
    this.getAccess = getAccess;
  }

  @Override
  public ProjectAccessInfo get() throws RestApiException {
    try {
      return getAccess.apply(project);
    } catch (IOException e) {
      throw new RestApiException("Cannot get access rights", e);
    }
  }
}
