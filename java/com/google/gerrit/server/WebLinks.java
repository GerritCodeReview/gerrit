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
import com.google.gerrit.extensions.webui.EditWebLink;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.ParentWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.gerrit.extensions.webui.ResolveConflictsWebLink;
import com.google.gerrit.extensions.webui.TagWebLink;
import com.google.gerrit.extensions.webui.WebLink;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Function;

@Singleton
public class WebLinks {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicSet<PatchSetWebLink> patchSetLinks;
  private final DynamicSet<ResolveConflictsWebLink> resolveConflictsLinks;
  private final DynamicSet<ParentWebLink> parentLinks;
  private final DynamicSet<EditWebLink> editLinks;
  private final DynamicSet<FileWebLink> fileLinks;
  private final DynamicSet<FileHistoryWebLink> fileHistoryLinks;
  private final DynamicSet<DiffWebLink> diffLinks;
  private final DynamicSet<ProjectWebLink> projectLinks;
  private final DynamicSet<BranchWebLink> branchLinks;
  private final DynamicSet<TagWebLink> tagLinks;

  @Inject
  public WebLinks(
      DynamicSet<PatchSetWebLink> patchSetLinks,
      DynamicSet<ResolveConflictsWebLink> resolveConflictsLinks,
      DynamicSet<ParentWebLink> parentLinks,
      DynamicSet<EditWebLink> editLinks,
      DynamicSet<FileWebLink> fileLinks,
      DynamicSet<FileHistoryWebLink> fileLogLinks,
      DynamicSet<DiffWebLink> diffLinks,
      DynamicSet<ProjectWebLink> projectLinks,
      DynamicSet<BranchWebLink> branchLinks,
      DynamicSet<TagWebLink> tagLinks) {
    this.patchSetLinks = patchSetLinks;
    this.resolveConflictsLinks = resolveConflictsLinks;
    this.parentLinks = parentLinks;
    this.editLinks = editLinks;
    this.fileLinks = fileLinks;
    this.fileHistoryLinks = fileLogLinks;
    this.diffLinks = diffLinks;
    this.projectLinks = projectLinks;
    this.branchLinks = branchLinks;
    this.tagLinks = tagLinks;
  }

  /**
   * Returns links for patch sets
   *
   * @param project Project name.
   * @param commit SHA1 of commit.
   * @param commitMessage the commit message of the commit.
   * @param branchName branch of the commit.
   * @param changeKey change Identifier for this change
   * @param changeId the numeric changeID for this change
   */
  public ImmutableList<WebLinkInfo> getPatchSetLinks(
      Project.NameKey project,
      String commit,
      String commitMessage,
      String branchName,
      String changeKey,
      int changeId) {
    return filterLinks(
        patchSetLinks,
        webLink ->
            webLink.getPatchSetWebLink(
                project.get(), commit, commitMessage, branchName, changeKey, changeId));
  }

  /**
   * Returns links for resolving conflicts
   *
   * @param project Project name.
   * @param commit SHA1 of commit.
   * @param commitMessage the commit message of the commit.
   * @param branchName branch of the commit.
   */
  public ImmutableList<WebLinkInfo> getResolveConflictsLinks(
      Project.NameKey project, String commit, String commitMessage, String branchName) {
    return filterLinks(
        resolveConflictsLinks,
        webLink ->
            webLink.getResolveConflictsWebLink(project.get(), commit, commitMessage, branchName));
  }

  /**
   * Returns links for patch sets
   *
   * @param project Project name.
   * @param revision SHA1 of the parent revision.
   * @param commitMessage the commit message of the parent revision.
   * @param branchName branch of the revision (and parent revision).
   */
  public ImmutableList<WebLinkInfo> getParentLinks(
      Project.NameKey project, String revision, String commitMessage, String branchName) {
    return filterLinks(
        parentLinks,
        webLink -> webLink.getParentWebLink(project.get(), revision, commitMessage, branchName));
  }

  /**
   * Returns links for editing
   *
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   */
  public ImmutableList<WebLinkInfo> getEditLinks(String project, String revision, String file) {
    return Patch.isMagic(file)
        ? ImmutableList.of()
        : filterLinks(editLinks, webLink -> webLink.getEditWebLink(project, revision, file));
  }

  /**
   * Returns links for files
   *
   * @param project Project name.
   * @param revision Name of the revision (e.g. branch or commit ID)
   * @param hash SHA1 of revision.
   * @param file File name.
   */
  public ImmutableList<WebLinkInfo> getFileLinks(
      String project, String revision, String hash, String file) {
    return Patch.isMagic(file)
        ? ImmutableList.of()
        : filterLinks(fileLinks, webLink -> webLink.getFileWebLink(project, revision, hash, file));
  }

  /**
   * Returns links for file history
   *
   * @param project Project name.
   * @param revision SHA1 of revision.
   * @param file File name.
   */
  public ImmutableList<WebLinkInfo> getFileHistoryLinks(
      String project, String revision, String file) {
    if (Patch.isMagic(file)) {
      return ImmutableList.of();
    }
    return fileHistoryLinks.stream()
        .map(webLink -> webLink.getFileHistoryWebLink(project, revision, file))
        .filter(WebLinks::isValid)
        .collect(toImmutableList());
  }

  /**
   * Returns links for file diffs
   *
   * @param project Project name.
   * @param patchSetIdA Patch set ID of side A, <code>null</code> if no base patch set was selected.
   * @param revisionA SHA1 of revision of side A.
   * @param fileA File name of side A.
   * @param patchSetIdB Patch set ID of side B.
   * @param revisionB SHA1 of revision of side B.
   * @param fileB File name of side B.
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
    return diffLinks.stream()
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
        .filter(WebLinks::isValid)
        .collect(toImmutableList());
  }

  /**
   * Returns links for projects
   *
   * @param project Project name.
   */
  public ImmutableList<WebLinkInfo> getProjectLinks(String project) {
    return filterLinks(projectLinks, webLink -> webLink.getProjectWeblink(project));
  }

  /**
   * Returns links for branches
   *
   * @param project Project name
   * @param branch Branch name
   */
  public ImmutableList<WebLinkInfo> getBranchLinks(String project, String branch) {
    return filterLinks(branchLinks, webLink -> webLink.getBranchWebLink(project, branch));
  }

  /**
   * Returns links for the tag
   *
   * @param project Project name
   * @param tag Tag name
   */
  public ImmutableList<WebLinkInfo> getTagLinks(String project, String tag) {
    return filterLinks(tagLinks, webLink -> webLink.getTagWebLink(project, tag));
  }

  private <T extends WebLink> ImmutableList<WebLinkInfo> filterLinks(
      DynamicSet<T> links, Function<T, WebLinkInfo> transformer) {
    return links.stream()
        .map(transformer)
        .filter(WebLinks::isValid)
        .collect(toImmutableList());
  }

  private static boolean isValid(WebLinkInfo link) {
    if (link == null) {
      return false;
    } else if (Strings.isNullOrEmpty(link.name) || Strings.isNullOrEmpty(link.url)) {
      logger.atWarning().log("%s is missing name and/or url", link.getClass().getName());
      return false;
    }
    return true;
  }
}
