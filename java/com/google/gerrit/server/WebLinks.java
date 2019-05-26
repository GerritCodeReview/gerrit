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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.DiffWebLink;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.ParentWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.gerrit.extensions.webui.TagWebLink;
import com.google.gerrit.extensions.webui.WebLink;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

@Singleton
public class WebLinks {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Predicate<WebLinkInfo> INVALID_WEBLINK =
      link -> {
        if (link == null) {
          return false;
        } else if (Strings.isNullOrEmpty(link.name) || Strings.isNullOrEmpty(link.url)) {
          logger.atWarning().log("%s is missing name and/or url", link.getClass().getName());
          return false;
        }
        return true;
      };

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<ParentWebLink> parentLinks;
  private final DynamicSet<FileWebLink> fileLinks;
  private final DynamicSet<FileHistoryWebLink> fileHistoryLinks;
  private final DynamicSet<DiffWebLink> diffLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;
  private final DynamicSet<BranchWebLink> branchLinks;
  private final DynamicSet<TagWebLink> tagLinks;

  @Inject
  public WebLinks(
      DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<ParentWebLink> parentLinks,
      DynamicSet<FileWebLink> fileLinks,
      DynamicSet<FileHistoryWebLink> fileLogLinks,
      DynamicSet<DiffWebLink> diffLinks,
      DynamicSet<ProjectWebLink> projectLinks,
      DynamicSet<BranchWebLink> branchLinks,
      DynamicSet<TagWebLink> tagLinks) {
    this.patchSetLinks = patchSetLinks;
    this.parentLinks = parentLinks;
    this.fileLinks = fileLinks;
    this.fileHistoryLinks = fileLogLinks;
    this.diffLinks = diffLinks;
    this.projectLinks = projectLinks;
    this.branchLinks = branchLinks;
    this.tagLinks = tagLinks;
  }

  /**
   * @param project Project name.
   * @param commit SHA1 of commit.
   * @return Links for patch sets.
   */
  public ImmutableList<WebLinkInfo> getPatchSetLinks(Project.NameKey project, String commit) {
    return filterLinks(patchSetLinks, webLink -> webLink.getPatchSetWebLink(project.get(), commit));
  }

  /**
   * @param project Project name.
   * @param revision SHA1 of the parent revision.
   * @return Links for patch sets.
   */
  public ImmutableList<WebLinkInfo> getParentLinks(Project.NameKey project, String revision) {
    return filterLinks(parentLinks, webLink -> webLink.getParentWebLink(project.get(), revision));
  }

  /**
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   * @return Links for files.
   */
  public ImmutableList<WebLinkInfo> getFileLinks(String project, String revision, String file) {
    return Patch.isMagic(file)
        ? ImmutableList.of()
        : filterLinks(fileLinks, webLink -> webLink.getFileWebLink(project, revision, file));
  }

  /**
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   * @return Links for file history
   */
  public ImmutableList<WebLinkInfo> getFileHistoryLinks(
      String project, String revision, String file) {
    if (Patch.isMagic(file)) {
      return ImmutableList.of();
    }
    return Streams.stream(fileHistoryLinks)
        .map(webLink -> webLink.getFileHistoryWebLink(project, revision, file))
        .filter(INVALID_WEBLINK)
        .collect(toImmutableList());
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
  public ImmutableList<DiffWebLinkInfo> getDiffLinks(
      String project,
      int changeId,
      Integer patchSetIdA,
      String revisionA,
      String fileA,
      int patchSetIdB,
      String revisionB,
      String fileB) {
    if (Patch.isMagic(fileA) || Patch.isMagic(fileB)) {
      return ImmutableList.of();
    }
    return Streams.stream(diffLinks)
        .map(
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
        .filter(INVALID_WEBLINK)
        .collect(toImmutableList());
  }

  /**
   * @param project Project name.
   * @return Links for projects.
   */
  public ImmutableList<WebLinkInfo> getProjectLinks(String project) {
    return filterLinks(projectLinks, webLink -> webLink.getProjectWeblink(project));
  }

  /**
   * @param project Project name
   * @param branch Branch name
   * @return Links for branches.
   */
  public ImmutableList<WebLinkInfo> getBranchLinks(String project, String branch) {
    return filterLinks(branchLinks, webLink -> webLink.getBranchWebLink(project, branch));
  }

  /**
   * @param project Project name
   * @param tag Tag name
   * @return Links for tags.
   */
  public ImmutableList<WebLinkInfo> getTagLinks(String project, String tag) {
    return filterLinks(tagLinks, webLink -> webLink.getTagWebLink(project, tag));
  }

  private <T extends WebLink> ImmutableList<WebLinkInfo> filterLinks(
      DynamicSet<T> links, Function<T, WebLinkInfo> transformer) {
    return Streams.stream(links)
        .map(transformer)
        .filter(INVALID_WEBLINK)
        .collect(toImmutableList());
  }
}
