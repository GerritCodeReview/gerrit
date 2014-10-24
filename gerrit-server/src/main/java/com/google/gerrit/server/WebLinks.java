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
            log.warn(String.format("%s is missing name and/or url",
                link.getClass().getName()));
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
   * @return Links for patch sets.
   */
  public FluentIterable<WebLinkInfo> getPatchSetLinks(final String project,
      final String commit) {
    return filterLinks(patchSetLinks, new Function<WebLink, WebLinkInfo>() {

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
   * @return Links for files.
   */
  public FluentIterable<WebLinkInfo> getFileLinks(final String project, final String revision,
      final String file) {
    return filterLinks(fileLinks, new Function<WebLink, WebLinkInfo>() {

      @Override
      public WebLinkInfo apply(WebLink webLink) {
        return ((FileWebLink)webLink).getFileWebLink(project, revision, file);
      }
    });
  }

  /**
   *
   * @param project Project name.
   * @return Links for projects.
   */
  public FluentIterable<WebLinkInfo> getProjectLinks(final String project) {
    return filterLinks(projectLinks, new Function<WebLink, WebLinkInfo>() {

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
   * @return Links for branches.
   */
  public FluentIterable<WebLinkInfo> getBranchLinks(final String project, final String branch) {
    return filterLinks(branchLinks, new Function<WebLink, WebLinkInfo>() {

      @Override
      public WebLinkInfo apply(WebLink webLink) {
        return ((BranchWebLink)webLink).getBranchWebLink(project, branch);
      }
    });
  }

  private FluentIterable<WebLinkInfo> filterLinks(DynamicSet<? extends WebLink> links,
      Function<WebLink, WebLinkInfo> transformer) {
    return FluentIterable
        .from(links)
        .transform(transformer)
        .filter(INVALID_WEBLINK);
  }
}