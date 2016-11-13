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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.Util;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Image;

public class ProjectSearchLink extends InlineHyperlink {

  public ProjectSearchLink(Project.NameKey projectName) {
    super(" ", PageLinks.toProjectDefaultDashboard(projectName));
    setTitle(Util.C.projectListQueryLink());
    final Image image = new Image(Gerrit.RESOURCES.queryIcon());
    image.setStyleName(Gerrit.RESOURCES.css().queryIcon());
    DOM.insertBefore(getElement(), image.getElement(), DOM.getFirstChild(getElement()));
  }
}
