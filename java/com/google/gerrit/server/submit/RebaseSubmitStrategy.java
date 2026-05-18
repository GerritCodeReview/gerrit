// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.submit.CommitMergeStatus.EMPTY_COMMIT;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** This strategy covers RebaseAlways and RebaseIfNecessary ones. */
public class RebaseSubmitStrategy extends SubmitStrategy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final boolean rebaseAlways;

  RebaseSubmitStrategy(SubmitStrategy.Arguments args, boolean rebaseAlways) {
    super(args);
    this.rebaseAlways = rebaseAlways;
  }

  @Override
  public ImmutableList<SubmitStrategyOp> buildOps(Collection<CodeReviewCommit> toMerge) {
    List<CodeReviewCommit> sorted;
    try {
      sorted = args.rebaseSorter.sort(toMerge);
    } catch (IOException | StorageException e) {
      throw new StorageException("Commit sorting failed", e);
    }

    ImmutableList.Builder<SubmitStrategyOp> ops =
        ImmutableList.builderWithExpectedSize(sorted.size());
    boolean first = true;
    while (!sorted.isEmpty()) {
      CodeReviewCommit n = sorted.remove(0);
      if (first && args.mergeTip.getInitialTip() == null) {
        // TODO(tandrii): Cherry-Pick strategy does this too, but it's wrong
        // and can be fixed.
        ops.add(new FastForwardOp(args, n));
      } else if (n.getParentCount() == 0) {
        ops.add(new RebaseRootOp(n));
      } else {
        ops.add(new RebaseOneOp(n));
      }
      first = false;
    }
    return ops.build();
  }

  private class RebaseRootOp extends SubmitStrategyOp {
    private RebaseRootOp(CodeReviewCommit toMerge) {
      super(RebaseSubmitStrategy.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx) {
      // Refuse to merge a root commit into an existing branch, we cannot obtain
      // a delta for the cherry-pick to apply.
      toMerge.setStatusCode(CommitMergeStatus.CANNOT_REBASE_ROOT);
    }
  }

  private class RebaseOneOp extends SubmitStrategyOp {
    private RebaseChangeOp rebaseOp;
    private CodeReviewCommit newCommit;
    private PatchSet.Id newPatchSetId;

    private RebaseOneOp(CodeReviewCommit toMerge) {
      super(RebaseSubmitStrategy.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx)
        throws InvalidChangeOperationException,
            RestApiException,
            IOException,
            PermissionBackendException,
            DiffNotAvailableException {
      if (!rebaseAlways
          && args.mergeUtil.canFastForward(
              args.mergeSorter, args.mergeTip.getCurrentTip(), args.rw, toMerge)) {
        if (args.project.is(BooleanProjectConfig.REJECT_EMPTY_COMMIT)
            && toMerge.getTree().equals(toMerge.getParent(0).getTree())) {
          toMerge.setStatusCode(EMPTY_COMMIT);
          return;
        }

        args.mergeTip.moveTipTo(amendGitlink(toMerge), toMerge);
        toMerge.setStatusCode(CommitMergeStatus.CLEAN_MERGE);
        acceptMergeTip(args.mergeTip);
        return;
      }

      args.rw.parseBody(toMerge);
      newPatchSetId =
          ChangeUtil.nextPatchSetIdFromChangeRefs(
              ctx.getRepoView().getRefs(getId().toRefPrefix()).keySet(),
              toMerge.change().currentPatchSetId());
      // Stale read of patch set is ok; see comments in RebaseChangeOp.
      PatchSet origPs = args.psUtil.get(toMerge.getNotes(), toMerge.getPatchsetId());
      rebaseOp =
          args.rebaseFactory
              .create(toMerge.notes(), origPs, args.mergeTip.getCurrentTip())
              .setFireRevisionCreated(false)
              // Bypass approval copier since SubmitStrategyOp copy all approvals
              // later anyway.
              .setValidate(false)
              .setCheckAddPatchSetPermission(false)
              // RebaseAlways should set always modify commit message like
              // Cherry-Pick strategy.
              .setDetailedCommitMessage(rebaseAlways)
              // Do not post message after inserting new patchset because there
              // will be one about change being merged already.
              .setPostMessage(false)
              .setSendEmail(false)
              .setMatchAuthorToCommitterDate(
                  args.project.is(BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE))
              // The votes are automatically copied and they don't count as copied votes. See
              // method's javadoc.
              .setStoreCopiedVotes(/* storeCopiedVotes= */ false)
              .setVerifyNeedsRebase(/* verifyNeedsRebase= */ !rebaseAlways);

      try {
        rebaseOp.updateRepo(ctx);
      } catch (MergeConflictException | NoSuchChangeException e) {
        toMerge.setStatusCode(CommitMergeStatus.REBASE_MERGE_CONFLICT);
        throw new IntegrationConflictException(
            "Cannot rebase " + toMerge.name() + ": " + e.getMessage(), e);
      }

      newCommit = args.rw.parseCommit(rebaseOp.getRebasedCommit());
      newPatchSetId = rebaseOp.getPatchSetId();

      if (args.project.is(BooleanProjectConfig.REJECT_EMPTY_COMMIT)
          && newCommit.getTree().equals(newCommit.getParent(0).getTree())) {
        toMerge.setStatusCode(EMPTY_COMMIT);
        return;
      }
      newCommit = amendGitlink(newCommit);
      newCommit.copyFrom(toMerge);
      newCommit.setPatchsetId(newPatchSetId);
      newCommit.setStatusCode(CommitMergeStatus.CLEAN_REBASE);
      args.mergeTip.moveTipTo(newCommit, newCommit);
      args.commitStatus.put(args.mergeTip.getCurrentTip());
      acceptMergeTip(args.mergeTip);
    }

    @Nullable
    @Override
    public PatchSet updateChangeImpl(ChangeContext ctx)
        throws NoSuchChangeException, ResourceConflictException, IOException, BadRequestException {
      if (newCommit == null) {
        checkState(!rebaseAlways, "RebaseAlways must never fast forward");
        // otherwise, took the fast-forward option, nothing to do.
        return null;
      }

      PatchSet newPs;
      if (rebaseOp != null) {
        @SuppressWarnings("unused")
        var unused = rebaseOp.updateChange(ctx);

        newPs = rebaseOp.getPatchSet();
      } else {
        // CherryPick
        PatchSet prevPs = args.psUtil.current(ctx.getNotes());
        newPs =
            args.psUtil.insert(
                ctx.getRevWalk(),
                ctx.getUpdate(newPatchSetId),
                newPatchSetId,
                newCommit,
                prevPs != null ? prevPs.groups() : ImmutableList.of(),
                null,
                null);
      }
      ctx.getChange()
          .setCurrentPatchSet(
              args.patchSetInfoFactory.get(ctx.getRevWalk(), newCommit, newPatchSetId));
      newCommit.setNotes(ctx.getNotes());
      return newPs;
    }

    @Override
    public void postUpdateImpl(PostUpdateContext ctx) {
      if (rebaseOp != null) {
        rebaseOp.postUpdate(ctx);
      }
    }
  }

  private void acceptMergeTip(MergeTip mergeTip) {
    args.alreadyAccepted.add(mergeTip.getCurrentTip());
  }

  static boolean dryRun(
      SubmitDryRun.Arguments args, CodeReviewCommit mergeTip, CodeReviewCommit toMerge) {
    if (toMerge.getParentCount() == 0) {
      return mergeTip == null;
    }
    // If the parent of this change has already been merged on the target branch
    // (possibly with a different commit SHA but matching Change-Id), we can safely
    // check mergeability by simulating a cherry-pick/rebase of this commit.
    if (mergeTip != null
        && toMerge.getParentCount() == 1
        && isParentMerged(args, mergeTip, toMerge)) {
      return args.mergeUtil.canCherryPick(args.mergeSorter, args.repo, mergeTip, args.rw, toMerge);
    }
    // Test for merge instead of cherry pick to avoid false negatives
    // on commit chains.
    return args.mergeUtil.canMerge(args.mergeSorter, args.repo, mergeTip, toMerge);
  }

  private static boolean isParentMerged(
      SubmitDryRun.Arguments args, CodeReviewCommit mergeTip, CodeReviewCommit toMerge) {
    try (ObjectReader or = args.rw.getObjectReader().newReader();
        RevWalk rw = new RevWalk(or)) {
      RevCommit parent = toMerge.getParent(0);
      RevCommit mergeTipCommit = rw.parseCommit(mergeTip);
      RevCommit parentCommit = rw.parseCommit(parent);

      // Fast-path: if the parent commit is already reachable from the target
      // branch tip, we can bypass history walking entirely. This occurs when
      // the parent was merged without rewriting.
      if (rw.isMergedInto(parentCommit, mergeTipCommit)) {
        return true;
      }

      // If the fast-path is false, the parent commit might have been merged with a
      // different commit SHA but matching Change-Id (e.g. due to a rebase/rewrite).
      // We query the change index globally with the parent change's Change-Id footer,
      // then filter in memory by project.
      rw.parseBody(parentCommit);
      List<String> parentChangeIds = parentCommit.getFooterLines(FooterConstants.CHANGE_ID);
      if (!parentChangeIds.isEmpty()) {
        String parentChangeId = parentChangeIds.get(parentChangeIds.size() - 1);
        List<ChangeData> changes =
            args.queryProvider.get().byKey(Change.key(parentChangeId));

        for (ChangeData cd : changes) {
          if (!cd.project().equals(args.destBranch.project())) {
            continue;
          }
          try {
            if (cd.change().isMerged() && cd.currentPatchSet() != null) {
              RevCommit mergedCommit = rw.parseCommit(cd.currentPatchSet().commitId());
              // Reset the RevWalk to clear flags set during previous checks/fast-path isMergedInto.
              rw.reset();
              if (rw.isMergedInto(mergedCommit, mergeTipCommit)) {
                return true;
              }
            }
          } catch (IOException | StorageException e) {
            // If one candidate commit is missing (e.g. due to replication
            // delays) or NoteDb queries fail, log a warning and continue
            // checking the other candidates instead of crashing the entire
            // dry-run check.
            logger.atWarning().withCause(e).log(
                "Error checking/parsing candidate merged commit for change %s", cd.getId());
          }
        }
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Error checking if parent commit of %s was merged", toMerge.name());
    }
    return false;
  }
}
