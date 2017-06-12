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
import com.google.gerrit.common.data.WebLinkInfoCommon;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.DiffWebLink;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.gerrit.extensions.webui.WebLink;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WebLinks {
  private static final Logger log = LoggerFactory.getLogger(WebLinks.class);

  private static final Predicate<WebLinkInfo> INVALID_WEBLINK =
      link -> {
        if (link == null) {
          return false;
        } else if (Strings.isNullOrEmpty(link.name) || Strings.isNullOrEmpty(link.url)) {
          log.warn(String.format("%s is missing name and/or url", link.getClass().getName()));
          return false;
        }
        return true;
      };

  private static final Predicate<WebLinkInfoCommon> INVALID_WEBLINK_COMMON =
      link -> {
        if (link == null) {
          return false;
        } else if (Strings.isNullOrEmpty(link.name) || Strings.isNullOrEmpty(link.url)) {
          log.warn(String.format("%s is missing name and/or url", link.getClass().getName()));
          return false;
        }
        return true;
      };

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<FileWebLink> fileLinks;
  private final DynamicSet<FileHistoryWebLink> fileHistoryLinks;
  private final DynamicSet<DiffWebLink> diffLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;
  private final DynamicSet<BranchWebLink> branchLinks;

  @Inject
  public WebLinks(
      DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<FileWebLink> fileLinks,
      DynamicSet<FileHistoryWebLink> fileLogLinks,
      DynamicSet<DiffWebLink> diffLinks,
      DynamicSet<ProjectWebLink> projectLinks,
      DynamicSet<BranchWebLink> branchLinks) {
    this.patchSetLinks = patchSetLinks;
    this.fileLinks = fileLinks;
    this.fileHistoryLinks = fileLogLinks;
    this.diffLinks = diffLinks;
    this.projectLinks = projectLinks;
    this.branchLinks = branchLinks;
  }

  /**
   * @param project Project name.
   * @param commit SHA1 of commit.
   * @return Links for patch sets.
   */
  public FluentIterable<WebLinkInfo> getPatchSetLinks(Project.NameKey project, String commit) {
    return filterLinks(patchSetLinks, webLink -> webLink.getPatchSetWebLink(project.get(), commit));
  }

  /**
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   * @return Links for files.
   */
  public FluentIterable<WebLinkInfo> getFileLinks(String project, String revision, String file) {
    return filterLinks(fileLinks, webLink -> webLink.getFileWebLink(project, revision, file));
  }

  /**
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   * @return Links for file history
   */
  public FluentIterable<WebLinkInfo> getFileHistoryLinks(
      String project, String revision, String file) {
    return filterLinks(
        fileHistoryLinks, webLink -> webLink.getFileHistoryWebLink(project, revision, file));
  }

  public FluentIterable<WebLinkInfoCommon> getFileHistoryLinksCommon(
      String project, String revision, String file) {
    return FluentIterable.from(fileHistoryLinks)
        .transform(
            webLink -> {
              WebLinkInfo info = webLink.getFileHistoryWebLink(project, revision, file);
              if (info == null) {
                return null;
              }
              WebLinkInfoCommon commonInfo = new WebLinkInfoCommon();
              commonInfo.name = info.name;
              commonInfo.imageUrl = info.imageUrl;
              commonInfo.url = info.url;
              commonInfo.target = info.target;
              return commonInfo;
            })
        .filter(INVALID_WEBLINK_COMMON);
  }

  /**
   * @param project Project name.
   * @param patchSetIdA Patch set ID of side A, <code>null</code> if no base patch set was selected.
   * @param revisionA SHA1 of revision of side A.
   * @param fileA File name of side A.
   * @param patchSetIdB Patch set ID of side B.
   * @param revisionB SHA1 of revision of side B.
   * @param fileB File name of side B.
   * @return Links for file diffs.
   */
  public FluentIterable<DiffWebLinkInfo> getDiffLinks(
      final String project,
      final int changeId,
      final Integer patchSetIdA,
      final String revisionA,
      final String fileA,
      final int patchSetIdB,
      final String revisionB,
      final String fileB) {
    return FluentIterable.from(diffLinks)
        .transform(
            webLink ->
                webLink.getDiffLink(
                    project,
                    changeId,
                    patchSetIdA,
                    revisionA,
                    fileA,
                    patchSetIdB,
                    revisionB,
                    fileB))
        .filter(INVALID_WEBLINK);
  }

  /**
   * @param project Project name.
   * @return Links for projects.
   */
  public FluentIterable<WebLinkInfo> getProjectLinks(final String project) {
    return filterLinks(projectLinks, webLink -> webLink.getProjectWeblink(project));
  }

  /**
   * @param project Project name
   * @param branch Branch name
   * @return Links for branches.
   */
  public FluentIterable<WebLinkInfo> getBranchLinks(final String project, final String branch) {
    return filterLinks(branchLinks, webLink -> webLink.getBranchWebLink(project, branch));
  }

  private <T extends WebLink> FluentIterable<WebLinkInfo> filterLinks(
      DynamicSet<T> links, Function<T, WebLinkInfo> transformer) {
    return FluentIterable.from(links).transform(transformer).filter(INVALID_WEBLINK);
  }
}
