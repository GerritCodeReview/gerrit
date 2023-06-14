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

package com.google.gerrit.server.restapi.change;

import static java.util.concurrent.TimeUnit.DAYS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ParentCommitData;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetCommit implements RestReadView<RevisionResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern PATCHSET_REF_PATTERN =
      Pattern.compile("refs/changes/[0-9]{2}/([0-9]+)/([0-9]+)");

  private final GitRepositoryManager repoManager;
  private final RevisionJson.Factory json;
  private final Provider<InternalChangeQuery> queryProvider;

  private boolean addLinks;

  @Inject
  GetCommit(
      GitRepositoryManager repoManager,
      RevisionJson.Factory json,
      Provider<InternalChangeQuery> queryProvider) {
    this.repoManager = repoManager;
    this.json = json;
    this.queryProvider = queryProvider;
  }

  @Option(name = "--links", usage = "Include weblinks")
  public GetCommit setAddLinks(boolean addLinks) {
    this.addLinks = addLinks;
    return this;
  }

  @Override
  public Response<CommitInfo> apply(RevisionResource rsrc) throws IOException {
    Project.NameKey project = rsrc.getChange().getProject();
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(rsrc.getPatchSet().commitId());
      RevCommit[] parents = commit.getParents();
      List<ParentCommitData> parentData = new ArrayList<>();
      String targetBranch =
          rsrc.getPatchSet().branch().isPresent()
              ? rsrc.getPatchSet().branch().get()
              : rsrc.getChange().getDest().branch();
      for (RevCommit parent : parents) {
        Optional<ParentCommitData> p =
            getParentData(rsrc.getProject(), repo, rw, parent, targetBranch);
        p.ifPresent(parentData::add);
      }
      rw.parseBody(commit);
      CommitInfo info =
          json.create(ImmutableSet.of())
              .getCommitInfo(
                  rsrc.getProject(),
                  rw,
                  commit,
                  addLinks,
                  /* fillCommit= */ true,
                  rsrc.getPatchSet().branch().isPresent()
                      ? rsrc.getPatchSet().branch().get()
                      : rsrc.getChange().getDest().branch(),
                  rsrc.getChange().getKey().get(),
                  rsrc.getChange().getId().get(),
                  parentData);
      Response<CommitInfo> r = Response.ok(info);
      if (rsrc.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, DAYS));
      }
      return r;
    }
  }

  private Optional<ParentCommitData> getParentData(
      Project.NameKey project, Repository repo, RevWalk rw, RevCommit parent, String targetBranch) {
    Optional<ParentCommitData> parentData =
        getParentFromTargetBranch(project, repo, rw, parent, targetBranch);
    if (parentData.isPresent()) {
      return parentData;
    }
    return getParentFromAnotherGerritChange(project, repo, parent);
  }

  /**
   * Returns {@link ParentCommitData} if the parent commit is reachable from the target branch, or
   * {@link Optional#empty()} otherwise.
   */
  private Optional<ParentCommitData> getParentFromTargetBranch(
      Project.NameKey project, Repository repo, RevWalk rw, RevCommit parent, String targetBranch) {
    try {
      Ref targetBranchRef = repo.exactRef(targetBranch);
      if (targetBranchRef == null) {
        return Optional.empty();
      }
      RevCommit targetBranchCommit = rw.parseCommit(targetBranchRef.getObjectId());
      ReachabilityChecker checker = rw.getObjectReader().createReachabilityChecker(rw);
      Optional<RevCommit> unreachable =
          checker.areAllReachable(
              ImmutableList.of(parent), ImmutableList.of(targetBranchCommit).stream());
      if (unreachable.isEmpty()) {
        return Optional.of(
            ParentCommitData.builder()
                .branchName(Optional.of(targetBranch))
                .commitId(Optional.of(parent.getId()))
                .autoBuild());
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check if parent commit %s (project: %s) is merged into target branch %s",
          parent.getName(), project, targetBranch);
    }
    return Optional.empty();
  }

  /**
   * Returns {@link ParentCommitData} if the parent commit is another Gerrit change, or {@link
   * Optional#empty()} otherwise.
   */
  private Optional<ParentCommitData> getParentFromAnotherGerritChange(
      Project.NameKey project, Repository repo, RevCommit parent) {
    // There should be a path-set ref pointing to the parent.
    Set<Ref> parentRefs;
    try {
      parentRefs = repo.getRefDatabase().getTipsWithSha1(parent);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to lookup tips with SHA1 for parent %s (project: %s)", parent, project);
      return Optional.empty();
    }
    List<Ref> patchSetRefs =
        parentRefs.stream()
            .filter(
                r -> RefNames.isRefsChanges(r.getName()) && !RefNames.isNoteDbMetaRef(r.getName()))
            .collect(Collectors.toList());
    if (patchSetRefs.size() != 1) {
      logger.atWarning().log(
          "Could not find one patch-set ref pointing at commit %s (project: %s): %s",
          parent.getName(), project, patchSetRefs);
      return Optional.empty();
    }
    Matcher matcher = PATCHSET_REF_PATTERN.matcher(patchSetRefs.get(0).getName());
    if (!matcher.find()) {
      logger.atWarning().log(
          "Could not find a patch-set ref pointing at commit %s (project: %s)",
          parent.getName(), project);
      return Optional.empty();
    }
    Integer changeNumber = Integer.parseInt(matcher.group(1));
    Integer patchSetNumber = Integer.parseInt(matcher.group(2));
    List<ChangeData> changeData =
        queryProvider.get().byLegacyChangeId(Change.id(changeNumber)).stream()
            .collect(Collectors.toList());
    if (changeData.size() != 1) {
      logger.atWarning().log(
          "Did not find a single change associated with change number %s (project: %s): %s",
          changeNumber, project, changeData);
      return Optional.empty();
    }
    ChangeData singleData = changeData.get(0);
    return Optional.of(
        ParentCommitData.builder()
            .commitId(Optional.of(parent.getId()))
            .changeKey(Optional.of(singleData.change().getKey()))
            .changeNumber(Optional.of(changeNumber))
            .patchSetNumber(Optional.of(patchSetNumber))
            .changeStatus(Optional.of(singleData.change().getStatus()))
            .autoBuild());
  }
}
