// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;

import java.util.List;

public class WebLinks {

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;

  public WebLinks(DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<ProjectWebLink> projectLinks) {
    this.patchSetLinks = patchSetLinks;
    this.projectLinks = projectLinks;
  }

  public Iterable<WebLinkInfo> getPatchSetLinks(String project, String commit) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (PatchSetWebLink webLink : patchSetLinks) {
      links.add(new WebLinkInfo(webLink.getLinkName(),
          webLink.getPatchSetUrl(project, commit)));
    }
    return links;
  }

  public Iterable<WebLinkInfo> getProjectLinks(String project) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (ProjectWebLink webLink : projectLinks) {
      links.add(new WebLinkInfo(webLink.getLinkName(),
          webLink.getProjectUrl(project)));
    }
    return links;
  }
}
