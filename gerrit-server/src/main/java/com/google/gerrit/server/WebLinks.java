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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class WebLinks {

  private static final Logger log = LoggerFactory.getLogger(WebLinks.class);

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<FileWebLink> fileLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;
  private final DynamicSet<BranchWebLink> branchLinks;

  @Inject
  public WebLinks(DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<FileWebLink> fileLinks,
      DynamicSet<ProjectWebLink> projectLinks,
      DynamicSet<BranchWebLink> branchLinks) {
    this.patchSetLinks = patchSetLinks;
    this.fileLinks = fileLinks;
    this.projectLinks = projectLinks;
    this.branchLinks = branchLinks;
  }

  public List<WebLinkInfo> getPatchSetLinks(String project, String commit) {
    List<WebLinkInfo> links = new ArrayList<>(4);
    for (PatchSetWebLink webLink : patchSetLinks) {
      WebLinkInfo link = webLink.getPathSetWebLink(project, commit);
      addIfValid(links, link, webLink.getClass().getName());
    }
    return links;
  }

  public List<WebLinkInfo> getFileLinks(String project, String revision,
      String file) {
    List<WebLinkInfo> links = new ArrayList<>(4);
    for (FileWebLink webLink : fileLinks) {
      WebLinkInfo link = webLink.getFileWebLink(project, revision, file);
      addIfValid(links, link, webLink.getClass().getName());
    }
    return links;
  }

  public List<WebLinkInfo> getProjectLinks(String project) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (ProjectWebLink webLink : projectLinks) {
      WebLinkInfo link = webLink.getProjectWeblink(project);
      addIfValid(links, link, webLink.getClass().getName());
    }
    return links;
  }

  public List<WebLinkInfo> getBranchLinks(String project, String branch) {
    List<WebLinkInfo> links = Lists.newArrayList();
    for (BranchWebLink webLink : branchLinks) {
      WebLinkInfo link = webLink.getBranchWebLink(project, branch);
      addIfValid(links, link, webLink.getClass().getName());
    }
    return links;
  }

  private void addIfValid(List<WebLinkInfo> links, WebLinkInfo webLink, String className) {
    if (webLink != null) {
      if (Strings.isNullOrEmpty(webLink.name)
          || Strings.isNullOrEmpty(webLink.url)) {
        log.warn(String.format("Weblink %s is missing name and/or url",
            className));
      } else {
        links.add(webLink);
      }
    }
  }
}
