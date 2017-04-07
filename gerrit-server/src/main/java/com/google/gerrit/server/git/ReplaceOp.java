// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromReviewers;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.server.ApprovalCopier;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.git.ReceiveCommits.MagicBranchInput;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceOp implements BatchUpdateOp {
  public interface Factory {
    ReplaceOp create(
        ProjectControl projectControl,
        Branch.NameKey dest,
        boolean checkMergedInto,
        @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
        @Assisted("priorCommitId") ObjectId priorCommit,
        @Assisted("patchSetId") PatchSet.Id patchSetId,
        @Assisted("commitId") ObjectId commitId,
        PatchSetInfo info,
        List<String> groups,
        @Nullable MagicBranchInput magicBranch,
        @Nullable PushCertificate pushCertificate);
  }

  private static final Logger log = LoggerFactory.getLogger(ReplaceOp.class);

  private static final String CHANGE_IS_CLOSED = "change is closed";

  private final AccountResolver accountResolver;
  private final ApprovalCopier approvalCopier;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeMessagesUtil cmUtil;
  private final ExecutorService sendEmailExecutor;
  private final GitReferenceUpdated gitRefUpdated;
  private final RevisionCreated revisionCreated;
  private final CommentAdded commentAdded;
  private final MergedByPushOp.Factory mergedByPushOpFactory;
  private final PatchSetUtil psUtil;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;

  private final ProjectControl projectControl;
  private final Branch.NameKey dest;
  private final boolean checkMergedInto;
  private final PatchSet.Id priorPatchSetId;
  private final ObjectId priorCommitId;
  private final PatchSet.Id patchSetId;
  private final ObjectId commitId;
  private final PatchSetInfo info;
  private final MagicBranchInput magicBranch;
  private final PushCertificate pushCertificate;
  private List<String> groups = ImmutableList.of();

  private final Map<String, Short> approvals = new HashMap<>();
  private final MailRecipients recipients = new MailRecipients();
  private RevCommit commit;
  private Change change;
  private PatchSet newPatchSet;
  private ChangeKind changeKind;
  private ChangeMessage msg;
  private String rejectMessage;
  private MergedByPushOp mergedByPushOp;
  private RequestScopePropagator requestScopePropagator;
  private boolean updateRef;

  @Inject
  ReplaceOp(
      AccountResolver accountResolver,
      ApprovalCopier approvalCopier,
      ApprovalsUtil approvalsUtil,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeData.Factory changeDataFactory,
      ChangeKindCache changeKindCache,
      ChangeMessagesUtil cmUtil,
      GitReferenceUpdated gitRefUpdated,
      RevisionCreated revisionCreated,
      CommentAdded commentAdded,
      MergedByPushOp.Factory mergedByPushOpFactory,
      PatchSetUtil psUtil,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      @Assisted ProjectControl projectControl,
      @Assisted Branch.NameKey dest,
      @Assisted boolean checkMergedInto,
      @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
      @Assisted("priorCommitId") ObjectId priorCommitId,
      @Assisted("patchSetId") PatchSet.Id patchSetId,
      @Assisted("commitId") ObjectId commitId,
      @Assisted PatchSetInfo info,
      @Assisted List<String> groups,
      @Assisted @Nullable MagicBranchInput magicBranch,
      @Assisted @Nullable PushCertificate pushCertificate) {
    this.accountResolver = accountResolver;
    this.approvalCopier = approvalCopier;
    this.approvalsUtil = approvalsUtil;
    this.changeControlFactory = changeControlFactory;
    this.changeDataFactory = changeDataFactory;
    this.changeKindCache = changeKindCache;
    this.cmUtil = cmUtil;
    this.gitRefUpdated = gitRefUpdated;
    this.revisionCreated = revisionCreated;
    this.commentAdded = commentAdded;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.psUtil = psUtil;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.sendEmailExecutor = sendEmailExecutor;

    this.projectControl = projectControl;
    this.dest = dest;
    this.checkMergedInto = checkMergedInto;
    this.priorPatchSetId = priorPatchSetId;
    this.priorCommitId = priorCommitId.copy();
    this.patchSetId = patchSetId;
    this.commitId = commitId.copy();
    this.info = info;
    this.groups = groups;
    this.magicBranch = magicBranch;
    this.pushCertificate = pushCertificate;
    this.updateRef = true;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws Exception {
    commit = ctx.getRevWalk().parseCommit(commitId);
    ctx.getRevWalk().parseBody(commit);
    changeKind =
        changeKindCache.getChangeKind(
            projectControl.getProject().getNameKey(),
            ctx.getRevWalk(),
            ctx.getRepository().getConfig(),
            priorCommitId,
            commitId);

    if (checkMergedInto) {
      Ref mergedInto = findMergedInto(ctx, dest.get(), commit);
      if (mergedInto != null) {
        mergedByPushOp =
            mergedByPushOpFactory.create(requestScopePropagator, patchSetId, mergedInto.getName());
      }
    }

    if (updateRef) {
      ctx.addRefUpdate(new ReceiveCommand(ObjectId.zeroId(), commitId, patchSetId.toRefName()));
    }
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, OrmException, IOException {
    change = ctx.getChange();
    if (change == null || change.getStatus().isClosed()) {
      rejectMessage = CHANGE_IS_CLOSED;
      return false;
    }
    if (groups.isEmpty()) {
      PatchSet prevPs = psUtil.current(ctx.getDb(), ctx.getNotes());
      groups = prevPs != null ? prevPs.getGroups() : ImmutableList.<String>of();
    }

    ChangeUpdate update = ctx.getUpdate(patchSetId);
    update.setSubjectForCommit("Create patch set " + patchSetId.get());

    String reviewMessage = null;
    String psDescription = null;
    if (magicBranch != null) {
      recipients.add(magicBranch.getMailRecipients());
      reviewMessage = magicBranch.message;
      psDescription = magicBranch.message;
      approvals.putAll(magicBranch.labels);
      Set<String> hashtags = magicBranch.hashtags;
      if (hashtags != null && !hashtags.isEmpty()) {
        hashtags.addAll(ctx.getNotes().getHashtags());
        update.setHashtags(hashtags);
      }
      if (magicBranch.topic != null && !magicBranch.topic.equals(ctx.getChange().getTopic())) {
        update.setTopic(magicBranch.topic);
      }
      if (magicBranch.removePrivate) {
        change.setPrivate(false);
        update.setPrivate(false);
      } else if (magicBranch.isPrivate) {
        change.setPrivate(true);
        update.setPrivate(true);
      }
    }

    boolean draft = magicBranch != null && magicBranch.draft;
    if (change.getStatus() == Change.Status.DRAFT && !draft) {
      update.setStatus(Change.Status.NEW);
    }
    newPatchSet =
        psUtil.insert(
            ctx.getDb(),
            ctx.getRevWalk(),
            update,
            patchSetId,
            commitId,
            draft,
            groups,
            pushCertificate != null ? pushCertificate.toTextWithSignature() : null,
            psDescription);

    update.setPsDescription(psDescription);
    recipients.add(
        getRecipientsFromFooters(ctx.getDb(), accountResolver, draft, commit.getFooterLines()));
    recipients.remove(ctx.getAccountId());
    ChangeData cd = changeDataFactory.create(ctx.getDb(), ctx.getControl());
    MailRecipients oldRecipients = getRecipientsFromReviewers(cd.reviewers());
    Iterable<PatchSetApproval> newApprovals =
        approvalsUtil.addApprovalsForNewPatchSet(
            ctx.getDb(),
            update,
            projectControl.getLabelTypes(),
            newPatchSet,
            ctx.getControl(),
            approvals);
    approvalCopier.copy(ctx.getDb(), ctx.getControl(), newPatchSet, newApprovals);
    approvalsUtil.addReviewers(
        ctx.getDb(),
        update,
        projectControl.getLabelTypes(),
        change,
        newPatchSet,
        info,
        recipients.getReviewers(),
        oldRecipients.getAll());

    // Check if approvals are changing in with this update. If so, add current user to reviewers.
    // Note that this is done separately as addReviewers is filtering out the change owner as
    // reviewer which is needed in several other code paths.
    if (magicBranch != null && !magicBranch.labels.isEmpty()) {
      update.putReviewer(ctx.getAccountId(), REVIEWER);
    }

    recipients.add(oldRecipients);

    String approvalMessage =
        ApprovalsUtil.renderMessageWithApprovals(
            patchSetId.get(), approvals, scanLabels(ctx, approvals));
    String kindMessage = changeKindMessage(changeKind);
    StringBuilder message = new StringBuilder(approvalMessage);
    if (!Strings.isNullOrEmpty(kindMessage)) {
      message.append(kindMessage);
    } else {
      message.append('.');
    }
    if (!Strings.isNullOrEmpty(reviewMessage)) {
      message.append("\n").append(reviewMessage);
    }
    msg =
        ChangeMessagesUtil.newMessage(
            patchSetId,
            ctx.getUser(),
            ctx.getWhen(),
            message.toString(),
            ChangeMessagesUtil.TAG_UPLOADED_PATCH_SET);
    cmUtil.addChangeMessage(ctx.getDb(), update, msg);

    if (mergedByPushOp == null) {
      resetChange(ctx);
    } else {
      mergedByPushOp.setPatchSetProvider(Providers.of(newPatchSet)).updateChange(ctx);
    }

    return true;
  }

  private String changeKindMessage(ChangeKind changeKind) {
    switch (changeKind) {
      case MERGE_FIRST_PARENT_UPDATE:
      case TRIVIAL_REBASE:
      case NO_CHANGE:
        return ": Patch Set " + priorPatchSetId.get() + " was rebased.";
      case NO_CODE_CHANGE:
        return ": Commit message was updated.";
      case REWORK:
      default:
        return null;
    }
  }

  private Map<String, PatchSetApproval> scanLabels(ChangeContext ctx, Map<String, Short> approvals)
      throws OrmException {
    Map<String, PatchSetApproval> current = new HashMap<>();
    // We optimize here and only retrieve current when approvals provided
    if (!approvals.isEmpty()) {
      for (PatchSetApproval a :
          approvalsUtil.byPatchSetUser(
              ctx.getDb(), ctx.getControl(), priorPatchSetId, ctx.getAccountId())) {
        if (a.isLegacySubmit()) {
          continue;
        }

        LabelType lt = projectControl.getLabelTypes().byLabel(a.getLabelId());
        if (lt != null) {
          current.put(lt.getName(), a);
        }
      }
    }
    return current;
  }

  private void resetChange(ChangeContext ctx) {
    Change change = ctx.getChange();
    if (!change.currentPatchSetId().equals(priorPatchSetId)) {
      return;
    }

    if (magicBranch != null && magicBranch.topic != null) {
      change.setTopic(magicBranch.topic);
    }
    if (change.getStatus() == Change.Status.DRAFT && newPatchSet.isDraft()) {
      // Leave in draft status.
    } else {
      change.setStatus(Change.Status.NEW);
    }
    change.setCurrentPatchSet(info);

    List<String> idList = commit.getFooterLines(CHANGE_ID);
    if (idList.isEmpty()) {
      change.setKey(new Change.Key("I" + commitId.name()));
    } else {
      change.setKey(new Change.Key(idList.get(idList.size() - 1).trim()));
    }
  }

  @Override
  public void postUpdate(final Context ctx) throws Exception {
    // Normally the ref updated hook is fired by BatchUpdate, but ReplaceOp is
    // special because its ref is actually updated by ReceiveCommits, so from
    // BatchUpdate's perspective there is no ref update. Thus we have to fire it
    // manually.
    final Account account = ctx.getAccount();
    if (!updateRef) {
      gitRefUpdated.fire(
          ctx.getProject(), newPatchSet.getRefName(), ObjectId.zeroId(), commitId, account);
    }

    if (changeKind != ChangeKind.TRIVIAL_REBASE) {
      Runnable sender =
          new Runnable() {
            @Override
            public void run() {
              try {
                ReplacePatchSetSender cm =
                    replacePatchSetFactory.create(
                        projectControl.getProject().getNameKey(), change.getId());
                cm.setFrom(account.getId());
                cm.setPatchSet(newPatchSet, info);
                cm.setChangeMessage(msg.getMessage(), ctx.getWhen());
                if (magicBranch != null) {
                  cm.setNotify(magicBranch.notify);
                  cm.setAccountsToNotify(magicBranch.getAccountsToNotify());
                }
                cm.addReviewers(recipients.getReviewers());
                cm.addExtraCC(recipients.getCcOnly());
                cm.send();
              } catch (Exception e) {
                log.error("Cannot send email for new patch set " + newPatchSet.getId(), e);
              }
            }

            @Override
            public String toString() {
              return "send-email newpatchset";
            }
          };

      if (requestScopePropagator != null) {
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError =
            sendEmailExecutor.submit(requestScopePropagator.wrap(sender));
      } else {
        sender.run();
      }
    }

    NotifyHandling notify =
        magicBranch != null && magicBranch.notify != null ? magicBranch.notify : NotifyHandling.ALL;
    revisionCreated.fire(change, newPatchSet, ctx.getAccount(), ctx.getWhen(), notify);
    try {
      fireCommentAddedEvent(ctx);
    } catch (Exception e) {
      log.warn("comment-added event invocation failed", e);
    }
    if (mergedByPushOp != null) {
      mergedByPushOp.postUpdate(ctx);
    }
  }

  private void fireCommentAddedEvent(Context ctx) throws OrmException {
    if (approvals.isEmpty()) {
      return;
    }

    /* For labels that are not set in this operation, show the "current" value
     * of 0, and no oldValue as the value was not modified by this operation.
     * For labels that are set in this operation, the value was modified, so
     * show a transition from an oldValue of 0 to the new value.
     */
    ChangeControl changeControl =
        changeControlFactory.controlFor(ctx.getDb(), change, ctx.getUser());
    List<LabelType> labels = changeControl.getLabelTypes().getLabelTypes();
    Map<String, Short> allApprovals = new HashMap<>();
    Map<String, Short> oldApprovals = new HashMap<>();
    for (LabelType lt : labels) {
      allApprovals.put(lt.getName(), (short) 0);
      oldApprovals.put(lt.getName(), null);
    }
    for (Map.Entry<String, Short> entry : approvals.entrySet()) {
      if (entry.getValue() != 0) {
        allApprovals.put(entry.getKey(), entry.getValue());
        oldApprovals.put(entry.getKey(), (short) 0);
      }
    }

    commentAdded.fire(
        change, newPatchSet, ctx.getAccount(), null, allApprovals, oldApprovals, ctx.getWhen());
  }

  public PatchSet getPatchSet() {
    return newPatchSet;
  }

  public Change getChange() {
    return change;
  }

  public String getRejectMessage() {
    return rejectMessage;
  }

  public ReplaceOp setUpdateRef(boolean updateRef) {
    this.updateRef = updateRef;
    return this;
  }

  public ReplaceOp setRequestScopePropagator(RequestScopePropagator requestScopePropagator) {
    this.requestScopePropagator = requestScopePropagator;
    return this;
  }

  private static Ref findMergedInto(Context ctx, String first, RevCommit commit) {
    try {
      RefDatabase refDatabase = ctx.getRepository().getRefDatabase();

      Ref firstRef = refDatabase.exactRef(first);
      if (firstRef != null && isMergedInto(ctx.getRevWalk(), commit, firstRef)) {
        return firstRef;
      }

      for (Ref ref : refDatabase.getRefs(Constants.R_HEADS).values()) {
        if (isMergedInto(ctx.getRevWalk(), commit, ref)) {
          return ref;
        }
      }
      return null;
    } catch (IOException e) {
      log.warn("Can't check for already submitted change", e);
      return null;
    }
  }

  private static boolean isMergedInto(RevWalk rw, RevCommit commit, Ref ref) throws IOException {
    return rw.isMergedInto(commit, rw.parseCommit(ref.getObjectId()));
  }
}
