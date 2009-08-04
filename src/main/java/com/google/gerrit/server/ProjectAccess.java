// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * Access controls for a {@link CurrentUser} within a {@link Project}.
 */
public class ProjectAccess {
  interface Factory {
    ProjectAccess create(CurrentUser u, ProjectState entry);
  }

  private final CurrentUser user;
  private final ProjectState entry;

  @Inject
  ProjectAccess(@Assisted final CurrentUser u,
      @Assisted final ProjectState e) {
    user = u;
    entry = e;
  }
}
