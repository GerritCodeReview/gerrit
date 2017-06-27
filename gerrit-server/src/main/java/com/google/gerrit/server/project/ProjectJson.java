// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.ProjectInfo.LabelInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.TreeMap;

@Singleton
public class ProjectJson {

  private final AllProjectsName allProjects;
  private final WebLinks webLinks;

  @Inject
  ProjectJson(AllProjectsName allProjectsName, WebLinks webLinks) {
    this.allProjects = allProjectsName;
    this.webLinks = webLinks;
  }

  public ProjectInfo format(ProjectResource rsrc) {
    System.err.println("fmt prsrc");
    ProjectControl ctl = rsrc.getControl();
    ProjectInfo info = format(ctl.getProject());
    info.labels = new TreeMap<>();
    for (LabelType t : ctl.getLabelTypes().getLabelTypes()) {
      LabelInfo labelInfo = new ProjectInfo.LabelInfo();
      for (LabelValue value : t.getValues()) {
        // NOSUBMIT: the json comes out as     "values": {"-2": "This shall not be merged", .. }
        // should we get integer keys instead, and if yes, how to force that?
        labelInfo.values.put(new Integer(value.getValue()), value.getText());
      }

      info.labels.put(t.getName(), labelInfo);
    }

    return info;
  }

  public ProjectInfo format(Project p) {
    System.err.println("fmt");
    ProjectInfo info = new ProjectInfo();
    info.name = p.getName();
    Project.NameKey parentName = p.getParent(allProjects);
    info.parent = parentName != null ? parentName.get() : null;
    info.description = Strings.emptyToNull(p.getDescription());
    info.state = p.getState();
    info.id = Url.encode(info.name);
    List<WebLinkInfo> links = webLinks.getProjectLinks(p.getName());
    info.webLinks = links.isEmpty() ? null : links;
    return info;
  }
}
