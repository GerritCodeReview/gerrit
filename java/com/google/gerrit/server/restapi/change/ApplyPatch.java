// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchPatchSetInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.ApplyPatchUtil;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.patch.PatchApplier.Result.Error;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class ApplyPatch implements RestModifyView<ChangeResource, ApplyPatchPatchSetInput> {

  private final ContributorAgreementsChecker contributorAgreements;
  private final GitRepositoryManager gitManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ProjectCache projectCache;
  private final ChangeUtil changeUtil;
  private final PatchSetCreator patchSetCreator;

  @Inject
  ApplyPatch(
      ContributorAgreementsChecker contributorAgreements,
      GitRepositoryManager gitManager,
      Provider<InternalChangeQuery> queryProvider,
      ProjectCache projectCache,
      ChangeUtil changeUtil,
      PatchSetCreator patchSetCreator) {
    this.contributorAgreements = contributorAgreements;
    this.gitManager = gitManager;
    this.queryProvider = queryProvider;
    this.projectCache = projectCache;
    this.changeUtil = changeUtil;
    this.patchSetCreator = patchSetCreator;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, ApplyPatchPatchSetInput input)
      throws IOException,
          UpdateException,
          RestApiException,
          PermissionBackendException,
          ConfigInvalidException,
          NoSuchProjectException,
          InvalidChangeOperationException {
    if (input == null || input.patch == null || input.patch.patch == null) {
      throw new BadRequestException("patch required");
    }

    NameKey project = rsrc.getProject();
    contributorAgreements.check(project, rsrc.getUser());
    BranchNameKey destBranch = rsrc.getChange().getDest();

    try (Repository repo = gitManager.openRepository(project);
        // This inserter and revwalk *must* be passed to any BatchUpdates
        // created later on, to ensure the applied commit is flushed
        // before patch sets are updated.
        ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = oi.newReader();
        CodeReviewRevWalk revWalk = CodeReviewCommit.newRevWalk(reader)) {
      Ref destRef = repo.getRefDatabase().exactRef(destBranch.branch());
      if (destRef == null) {
        throw new ResourceNotFoundException(
            String.format("Branch %s does not exist.", destBranch.branch()));
      }
      ChangeData destChange = rsrc.getChangeData();
      patchSetCreator.validateChangeCanBeAppended(destChange, destBranch);

      if (!Strings.isNullOrEmpty(input.base) && Boolean.TRUE.equals(input.amend)) {
        throw new BadRequestException("amend only works with existing revisions. omit base.");
      }

      RevCommit latestPatchset = revWalk.parseCommit(destChange.currentPatchSet().commitId());
      RevCommit baseCommit;
      ImmutableList<RevCommit> parents;
      if (!Strings.isNullOrEmpty(input.base)) {
        baseCommit =
            CommitUtil.getBaseCommit(
                project.get(), queryProvider.get(), revWalk, destRef, input.base);
        parents = ImmutableList.of(baseCommit);
      } else {
        if (latestPatchset.getParentCount() != 1) {
          throw new BadRequestException(
              String.format(
                  "Cannot parse base commit for a change with none or multiple parents. Change ID:"
                      + " %s.",
                  destChange.getId()));
        }
        if (Boolean.TRUE.equals(input.amend)) {
          baseCommit = latestPatchset;
          parents = ImmutableList.copyOf(baseCommit.getParents());
        } else {
          baseCommit = revWalk.parseCommit(latestPatchset.getParent(0));
          parents = ImmutableList.of(baseCommit);
        }
      }

      List<ListChangesOption> opts = input.responseFormatOptions;
      if (opts == null) {
        opts = ImmutableList.of();
      }

      PatchApplier.Result applyResult =
          ApplyPatchUtil.applyPatch(repo, oi, input.patch, baseCommit);

      String commitMessage =
          buildFullCommitMessage(
              project,
              latestPatchset,
              input,
              ApplyPatchUtil.getResultPatch(
                  repo, reader, baseCommit, revWalk.lookupTree(applyResult.getTreeId())),
              applyResult.getErrors());

      ChangeInfo changeInfo =
          patchSetCreator.createPatchSetWithSuppliedTree(
              project,
              destChange,
              latestPatchset,
              parents,
              input.author,
              opts,
              repo,
              oi,
              revWalk,
              applyResult.getTreeId(),
              commitMessage);
      if (changeInfo.containsGitConflicts == null
          && applyResult.getErrors().stream().anyMatch(Error::isGitConflict)) {
        changeInfo.containsGitConflicts = true;
      }
      return Response.ok(changeInfo);
    }
  }

  private String buildFullCommitMessage(
      NameKey project,
      RevCommit latestPatchset,
      ApplyPatchPatchSetInput input,
      String resultPatch,
      List<org.eclipse.jgit.patch.PatchApplier.Result.Error> errors)
      throws ResourceConflictException, BadRequestException {
    boolean hasInputCommitMessage = !Strings.isNullOrEmpty(input.commitMessage);
    String fullMessage =
        hasInputCommitMessage ? input.commitMessage : latestPatchset.getFullMessage();
    // Since we might add error information to the message, we need to split the footers from the
    // actual description.
    // TODO: Fix parsing footers from the commit message. FooterLine#fromMessage expects the raw
    // commit message that contains header lines, see RawParseUtils#commitMessage which is invoked
    // from FooterLine#fromMessage. RawParseUtils#commitMessage always increases the pointer by 46
    // to skip the "tree ..." line and if this line is not present the parsing of the footers is
    // broken. This can lead to no footers being found although a Change-Id footer is present. This
    // causes us to add the Change-Id again and as a result we end up with a commit message that
    // contains the Change-Id line twice.
    List<FooterLine> footerLines = FooterLine.fromMessage(fullMessage);
    String messageWithNoFooters = removeFooters(fullMessage, footerLines);
    if (FooterLine.getValues(footerLines, FOOTER_CHANGE_ID).isEmpty()) {
      footerLines.add(
          latestPatchset.getFooterLines().stream()
              .filter(f -> f.matches(FOOTER_CHANGE_ID))
              .findFirst()
              .get());
    }
    String commitMessage =
        ApplyPatchUtil.buildCommitMessage(
            messageWithNoFooters, footerLines, input.patch, resultPatch, errors);

    boolean changeIdRequired =
        projectCache
            .get(project)
            .orElseThrow(illegalState(project))
            .is(BooleanProjectConfig.REQUIRE_CHANGE_ID);
    changeUtil.ensureChangeIdIsCorrect(
        changeIdRequired, changeUtil.getChangeIdsFromFooter(latestPatchset).get(0), commitMessage);

    return commitMessage;
  }

  private String removeFooters(String originalMessage, List<FooterLine> footerLines) {
    if (footerLines.isEmpty()) {
      return originalMessage;
    }
    return originalMessage.substring(0, originalMessage.indexOf(footerLines.get(0).getKey()));
  }
}
