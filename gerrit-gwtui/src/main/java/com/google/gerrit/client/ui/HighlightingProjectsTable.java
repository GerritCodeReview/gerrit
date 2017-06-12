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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gwt.user.client.ui.InlineHTML;

public class HighlightingProjectsTable extends ProjectsTable {
  private String toHighlight;

  public void display(final ProjectMap projects, final String toHighlight) {
    this.toHighlight = toHighlight;
    super.display(projects);
  }

  @Override
  protected void populate(final int row, final ProjectInfo k) {
    populateState(row, k);
    table.setWidget(
        row, ProjectsTable.C_NAME, new InlineHTML(Util.highlight(k.name(), toHighlight)));
    table.setText(row, ProjectsTable.C_DESCRIPTION, k.description());

    setRowItem(row, k);
  }
}
