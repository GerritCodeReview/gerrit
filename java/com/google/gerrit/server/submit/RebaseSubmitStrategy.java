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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.submit.CommitMergeStatus.EMPTY_COMMIT;
import static com.google.gerrit.server.submit.CommitMergeStatus.SKIPPED_IDENTICAL_TREE;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
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
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** This strategy covers RebaseAlways and RebaseIfNecessary ones. */
public class RebaseSubmitStrategy extends SubmitStrategy {
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

    // We cannot rebase merge commits. This is why we integrate merge changes into the target branch
    // the same way as if MERGE_IF_NECESSARY was the submit strategy. This means if needed we create
    // a merge commit that integrates the merge change into the target branch.
    // If we integrate a change series that consists out of a normal change and a merge change,
    // where the merge change depends on the normal change, we must skip rebasing the normal change,
    // because it already gets integrated by merging the merge change. If the rebasing of the normal
    // change is not skipped, it would appear twice in the history after the submit is done (once
    // through its rebased commit, and once through its original commit which is a parent of the
    // merge change that was merged into the target branch. To skip the rebasing of the normal
    // change, we call MergeUtil#reduceToMinimalMerge, as it excludes commits which will be
    // implicitly integrated by merging the series. Then we use the MergeIfNecessaryOp to integrate
    // the whole series.
    // If on the other hand, we integrate a change series that consists out of a merge change and a
    // normal change, where the normal change depends on the merge change, we can first integrate
    // the merge change by a merge and then integrate the normal change by a rebase. In this case we
    // do not want to call MergeUtil#reduceToMinimalMerge as we are not intending to integrate the
    // whole series by a merge, but rather do the integration of the commits one by one.
    boolean foundNonMerge = false;
    for (CodeReviewCommit c : sorted) {
      if (c.getParentCount() > 1) {
        if (!foundNonMerge) {
          // found a merge change, but it doesn't depend on a normal change, this means we are not
          // required to merge the whole series at once
          continue;
        }
        // found a merge commit that depends on a normal change, this means we are required to merge
        // the whole series at once
        sorted = args.mergeUtil.reduceToMinimalMerge(args.mergeSorter, sorted);
        return sorted.stream().map(n -> new MergeIfNecessaryOp(n)).collect(toImmutableList());
      }
      foundNonMerge = true;
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
      } else if (n.getParentCount() == 1) {
        ops.add(new RebaseOneOp(n));
      } else {
        ops.add(new MergeIfNecessaryOp(n));
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
        throws InvalidChangeOperationException, RestApiException, IOException,
            PermissionBackendException {
      if (args.mergeUtil.canFastForward(
          args.mergeSorter, args.mergeTip.getCurrentTip(), args.rw, toMerge)) {
        if (!rebaseAlways) {
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
        // RebaseAlways means we modify commit message.
        args.rw.parseBody(toMerge);
        newPatchSetId =
            ChangeUtil.nextPatchSetIdFromChangeRefs(
                ctx.getRepoView().getRefs(getId().toRefPrefix()).keySet(),
                toMerge.change().currentPatchSetId());
        RevCommit mergeTip = args.mergeTip.getCurrentTip();
        args.rw.parseBody(mergeTip);
        String cherryPickCmtMsg = args.mergeUtil.createCommitMessageOnSubmit(toMerge, mergeTip);
        PersonIdent committer =
            Optional.ofNullable(toMerge.getCommitterIdent())
                .map(ident -> ctx.newCommitterIdent(ident.getEmailAddress(), args.caller))
                .orElseGet(() -> ctx.newCommitterIdent(args.caller));
        try {
          newCommit =
              args.mergeUtil.createCherryPickFromCommit(
                  ctx.getInserter(),
                  ctx.getRepoView().getConfig(),
                  args.mergeTip.getCurrentTip(),
                  toMerge,
                  committer,
                  cherryPickCmtMsg,
                  args.rw,
                  0,
                  true,
                  false);
        } catch (MergeConflictException mce) {
          // Unlike in Cherry-pick case, this should never happen.
          toMerge.setStatusCode(CommitMergeStatus.REBASE_MERGE_CONFLICT);
          throw new IllegalStateException(
              "MergeConflictException on message edit must not happen", mce);
        } catch (MergeIdenticalTreeException mie) {
          // this should not happen
          toMerge.setStatusCode(SKIPPED_IDENTICAL_TREE);
          return;
        }
        ctx.addRefUpdate(ObjectId.zeroId(), newCommit, newPatchSetId.toRefName());
      } else {
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
                .setStoreCopiedVotes(/* storeCopiedVotes = */ false);
        try {
          rebaseOp.updateRepo(ctx);
        } catch (MergeConflictException | NoSuchChangeException e) {
          toMerge.setStatusCode(CommitMergeStatus.REBASE_MERGE_CONFLICT);
          throw new IntegrationConflictException(
              "Cannot rebase " + toMerge.name() + ": " + e.getMessage(), e);
        }
        newCommit = args.rw.parseCommit(rebaseOp.getRebasedCommit());
        newPatchSetId = rebaseOp.getPatchSetId();
      }
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
        rebaseOp.updateChange(ctx);
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

  private class MergeIfNecessaryOp extends SubmitStrategyOp {
    private MergeIfNecessaryOp(CodeReviewCommit toMerge) {
      super(RebaseSubmitStrategy.this.args, toMerge);
    }

    @Override
    public void updateRepoImpl(RepoContext ctx) throws IntegrationConflictException, IOException {
      // There are multiple parents, so this is a merge commit. We don't want
      // to rebase the merge as clients can't easily rebase their history with
      // that merge present and replaced by an equivalent merge with a different
      // first parent. So instead behave as though MERGE_IF_NECESSARY was
      // configured.
      // TODO(tandrii): this is not in spirit of RebaseAlways strategy because
      // the commit messages can not be modified in the process. It's also
      // possible to implement rebasing of merge commits. E.g., the Cherry Pick
      // REST endpoint already supports cherry-picking of merge commits.
      // For now, users of RebaseAlways strategy for whom changed commit footers
      // are important would be well advised to prohibit uploading patches with
      // merge commits.
      MergeTip mergeTip = args.mergeTip;
      if (args.rw.isMergedInto(mergeTip.getCurrentTip(), toMerge)
          && !args.subscriptionGraph.hasSubscription(args.destBranch)) {
        mergeTip.moveTipTo(toMerge, toMerge);
      } else {
        PersonIdent caller = ctx.newCommitterIdent();
        CodeReviewCommit newTip =
            args.mergeUtil.mergeOneCommit(
                caller,
                caller,
                args.rw,
                ctx.getInserter(),
                ctx.getRepoView().getConfig(),
                args.destBranch,
                mergeTip.getCurrentTip(),
                toMerge);
        mergeTip.moveTipTo(amendGitlink(newTip), toMerge);
      }
      args.mergeUtil.markCleanMerges(
          args.rw, args.canMergeFlag, mergeTip.getCurrentTip(), args.alreadyAccepted);
      acceptMergeTip(mergeTip);
    }
  }

  private void acceptMergeTip(MergeTip mergeTip) {
    args.alreadyAccepted.add(mergeTip.getCurrentTip());
  }

  static boolean dryRun(
      SubmitDryRun.Arguments args,
      Repository repo,
      CodeReviewCommit mergeTip,
      CodeReviewCommit toMerge) {
    // Test for merge instead of cherry pick to avoid false negatives
    // on commit chains.
    return args.mergeUtil.canMerge(args.mergeSorter, repo, mergeTip, toMerge);
  }
}
