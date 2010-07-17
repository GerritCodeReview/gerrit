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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Change.Status;

/** Link to the open changes of a project. */
public class ProjectLink extends InlineHyperlink {
  private Project.NameKey project;
  private Status status;

  public ProjectLink(final Project.NameKey proj, Change.Status stat) {
    this(proj.get(), proj, stat);
  }

  public ProjectLink(final String text, final Project.NameKey proj,
      Change.Status stat) {
    super(text, PageLinks.toProject(proj, stat));
    status = stat;
    project = proj;
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }

  private Screen createScreen() {
    switch (status) {
      case ABANDONED:
        return QueryScreen.forQuery("status:abandoned "
            + QueryScreen.op("project", project.get()));

      case MERGED:
        return QueryScreen.forQuery("status:merged "
            + QueryScreen.op("project", project.get()));

      case NEW:
      case SUBMITTED:
      default:
        return QueryScreen.forQuery("status:open "
            + QueryScreen.op("project", project.get()));
    }
  }
}
