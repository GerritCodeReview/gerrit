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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Link;
import com.google.gerrit.client.changes.ByProjectAbandonedChangesScreen;
import com.google.gerrit.client.changes.ByProjectMergedChangesScreen;
import com.google.gerrit.client.changes.ByProjectOpenChangesScreen;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.Change.Status;

/** Link to the open changes of a project. */
public class ProjectLink extends DirectScreenLink {
  private Project.NameKey project;
  private Status status;

  public ProjectLink(final Project.NameKey proj, Change.Status stat) {
    this(proj.get(), proj, stat);
  }

  public ProjectLink(final String text, final Project.NameKey proj,
      Change.Status stat) {
    super(text, Link.toProject(proj, stat));
    status = stat;
    project = proj;
  }

  @Override
  protected Screen createScreen() {
    switch (status) {
      case ABANDONED:
        return new ByProjectAbandonedChangesScreen(project, "n,z");
      case MERGED:
        return new ByProjectMergedChangesScreen(project, "n,z");
      default:
        return new ByProjectOpenChangesScreen(project, "n,z");
    }
  }
}
