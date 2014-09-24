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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.gerrit.extensions.webui.WebLink;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;

import java.util.List;

@Singleton
public class WebLinks {

  private final DynamicSet<WebLink<RevisionResource>> patchSetLinks;
  private final DynamicSet<WebLink<FileResource>> fileLinks;
  private final DynamicSet<WebLink<ProjectResource>> projectLinks;
  private final DynamicSet<WebLink<BranchResource>> branchLinks;

  @Inject
  public WebLinks(DynamicSet<WebLink<RevisionResource>> patchSetLinks,
      DynamicSet<WebLink<FileResource>> fileLinks,
      DynamicSet<WebLink<ProjectResource>> projectLinks,
      DynamicSet<WebLink<BranchResource>> branchLinks) {
    this.patchSetLinks = patchSetLinks;
    this.fileLinks = fileLinks;
    this.projectLinks = projectLinks;
    this.branchLinks = branchLinks;
  }


  public Iterable<WebLinkInfo> getPatchSetLinks(RevisionResource resource) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (WebLink<RevisionResource> webLink : patchSetLinks) {
      links.add(webLink.getWebLinkInfoFor(resource));
    }
    return links;
  }

  public Iterable<WebLinkInfo> getFileLinks(FileResource resource) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (WebLink<FileResource> webLink : fileLinks) {
      links.add(webLink.getWebLinkInfoFor(resource));
    }
    return links;
  }

  public Iterable<WebLinkInfo> getProjectLinks(ProjectResource resource) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (WebLink<ProjectResource> webLink : projectLinks) {
      links.add(webLink.getWebLinkInfoFor(resource));
    }
    return links;
  }

  public Iterable<WebLinkInfo> getBranchLinks(BranchResource resource) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (WebLink<BranchResource> webLink : branchLinks) {
      links.add(webLink.getWebLinkInfoFor(resource));
    }
    return links;
  }
}
