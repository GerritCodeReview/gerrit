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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.gerrit.extensions.webui.WebLink;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@Singleton
public class WebLinks {

  private static final Logger log = LoggerFactory.getLogger(WebLinks.class);
  private static final Predicate<WebLinkInfo> INVALID_WEBLINK =
      new Predicate<WebLinkInfo>() {

        @Override
        public boolean apply(WebLinkInfo link) {
          if (link == null){
            return false;
          } else if (Strings.isNullOrEmpty(link.name)
              || Strings.isNullOrEmpty(link.url)) {
            log.warn("Weblink is missing name and/or url");
            return false;
          }
          return true;
        }
      };

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

  /**
   *
   * @param project Project name.
   * @param commit SHA1 of commit.
   * @return Null if no valid WebLinks are available for PatchSet.
   */
  public List<WebLinkInfo> getPatchSetLinks(final String project,
      final String commit) {
    return linksToList(patchSetLinks, new Function<WebLink, WebLinkInfo>() {

      @Override
      public WebLinkInfo apply(WebLink webLink) {
        return ((PatchSetWebLink)webLink).getPathSetWebLink(project, commit);
      }
    });
  }

  /**
   *
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   * @return Null if no valid WebLinks are available for file.
   */
  public List<WebLinkInfo> getFileLinks(final String project, final String revision,
      final String file) {
    return linksToList(fileLinks, new Function<WebLink, WebLinkInfo>() {

      @Override
      public WebLinkInfo apply(WebLink webLink) {
        return ((FileWebLink)webLink).getFileWebLink(project, revision, file);
      }
    });
  }

  /**
   *
   * @param project Project name.
   * @return Null if no valid WebLinks are available for project.
   */
  public List<WebLinkInfo> getProjectLinks(final String project) {
    return linksToList(projectLinks, new Function<WebLink, WebLinkInfo>() {

      @Override
      public WebLinkInfo apply(WebLink webLink) {
        return ((ProjectWebLink)webLink).getProjectWeblink(project);
      }
    });
  }

  /**
   *
   * @param project Project name
   * @param branch Branch name
   * @return Null if no valid WebLinks are available for branch.
   */
  public List<WebLinkInfo> getBranchLinks(final String project, final String branch) {
    return linksToList(branchLinks, new Function<WebLink, WebLinkInfo>() {

      @Override
      public WebLinkInfo apply(WebLink webLink) {
        return ((BranchWebLink)webLink).getBranchWebLink(project, branch);
      }
    });
  }

  private List<WebLinkInfo> linksToList(DynamicSet<? extends WebLink> links,
      Function<WebLink, WebLinkInfo> transFormer) {
    FluentIterable<WebLinkInfo> linkInfos =
        FluentIterable
        .from(links)
        .transform(transFormer)
        .filter(INVALID_WEBLINK);
    return linkInfos.isEmpty() ? null : linkInfos.toList();
  }
}