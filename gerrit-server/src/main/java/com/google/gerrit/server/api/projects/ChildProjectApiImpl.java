// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.api.projects.ChildProjectApi;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.ChildProjectResource;
import com.google.gerrit.server.project.GetChildProject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class ChildProjectApiImpl implements ChildProjectApi {
  interface Factory {
    ChildProjectApiImpl create(ChildProjectResource rsrc);
  }

  private final GetChildProject getChildProject;
  private final ChildProjectResource rsrc;

  @AssistedInject
  ChildProjectApiImpl(GetChildProject getChildProject, @Assisted ChildProjectResource rsrc) {
    this.getChildProject = getChildProject;
    this.rsrc = rsrc;
  }

  @Override
  public ProjectInfo get() throws RestApiException {
    return get(false);
  }

  @Override
  public ProjectInfo get(boolean recursive) throws RestApiException {
    getChildProject.setRecursive(recursive);
    return getChildProject.apply(rsrc);
  }
}
