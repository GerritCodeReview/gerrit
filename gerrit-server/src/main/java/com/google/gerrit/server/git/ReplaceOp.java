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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.server.ApprovalCopier;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeKind;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.ReceiveCommits.MagicBranchInput;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class ReplaceOp extends BatchUpdate.Op {
  public interface Factory {
    ReplaceOp create(
        RequestScopePropagator requestScopePropagator,
        ProjectControl projectControl,
        boolean checkMergedInto,
        @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
        @Assisted("priorCommit") RevCommit priorCommit,
        @Assisted("patchSetId") PatchSet.Id patchSetId,
        @Assisted("commit") RevCommit commit,
        PatchSetInfo info,
        List<String> groups,
        @Nullable MagicBranchInput magicBranch,
        @Nullable PushCertificate pushCertificate);
  }

  private static final Logger log =
      LoggerFactory.getLogger(ReplaceOp.class);

  private static final String CHANGE_IS_CLOSED = "change is closed";

  private final PatchSetUtil psUtil;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final ChangeKindCache changeKindCache;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeHooks hooks;
  private final ApprovalsUtil approvalsUtil;
  private final ApprovalCopier approvalCopier;
  private final AccountResolver accountResolver;
  private final ExecutorService sendEmailExecutor;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final MergedSender.Factory mergedSenderFactory;

  private final RequestScopePropagator requestScopePropagator;
  private final ProjectControl projectControl;
  private final boolean checkMergedInto;
  private final PatchSet.Id priorPatchSetId;
  private final RevCommit priorCommit;
  private final PatchSet.Id patchSetId;
  private final RevCommit commit;
  private final PatchSetInfo info;
  private final MagicBranchInput magicBranch;
  private final PushCertificate pushCertificate;
  private List<String> groups = ImmutableList.of();

  private final Map<String, Short> approvals = new HashMap<>();
  private final MailRecipients recipients = new MailRecipients();
  private Change change;
  private PatchSet newPatchSet;
  private ChangeKind changeKind;
  private ChangeMessage msg;
  private String rejectMessage;
  private String mergedIntoRef;

  @AssistedInject
  ReplaceOp(PatchSetUtil psUtil,
      ChangeData.Factory changeDataFactory,
      ChangeControl.GenericFactory changeControlFactory,
      ChangeKindCache changeKindCache,
      ChangeMessagesUtil cmUtil,
      ChangeHooks hooks,
      ApprovalsUtil approvalsUtil,
      ApprovalCopier approvalCopier,
      AccountResolver accountResolver,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      MergedSender.Factory mergedSenderFactory,
      @Assisted RequestScopePropagator requestScopePropagator,
      @Assisted ProjectControl projectControl,
      @Assisted boolean checkMergedInto,
      @Assisted("priorPatchSetId") PatchSet.Id priorPatchSetId,
      @Assisted("priorCommit") RevCommit priorCommit,
      @Assisted("patchSetId") PatchSet.Id patchSetId,
      @Assisted("commit") RevCommit commit,
      @Assisted PatchSetInfo info,
      @Assisted List<String> groups,
      @Assisted @Nullable MagicBranchInput magicBranch,
      @Assisted @Nullable PushCertificate pushCertificate) {
    this.psUtil = psUtil;
    this.changeDataFactory = changeDataFactory;
    this.changeControlFactory = changeControlFactory;
    this.changeKindCache = changeKindCache;
    this.cmUtil = cmUtil;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.approvalCopier = approvalCopier;
    this.accountResolver = accountResolver;
    this.sendEmailExecutor = sendEmailExecutor;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.mergedSenderFactory = mergedSenderFactory;

    this.requestScopePropagator = requestScopePropagator;
    this.projectControl = projectControl;
    this.checkMergedInto = checkMergedInto;
    this.priorPatchSetId = priorPatchSetId;
    this.priorCommit = priorCommit;
    this.patchSetId = patchSetId;
    this.commit = commit;
    this.info = info;
    this.groups = groups;
    this.magicBranch = magicBranch;
    this.pushCertificate = pushCertificate;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws Exception {
    changeKind = changeKindCache.getChangeKind(projectControl.getProjectState(),
        ctx.getRepository(), priorCommit, commit);
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws OrmException, IOException {
    change = ctx.getChange();
    if (change == null || change.getStatus().isClosed()) {
      rejectMessage = CHANGE_IS_CLOSED;
      return false;
    }
    if (groups.isEmpty()) {
      PatchSet prevPs = psUtil.current(ctx.getDb(), ctx.getNotes());
      groups = prevPs != null
          ? prevPs.getGroups()
          : ImmutableList.<String> of();
    }

    ChangeUpdate update = ctx.getUpdate(patchSetId);
    update.setSubjectForCommit("Create patch set " + patchSetId.get());

    String reviewMessage = null;
    if (magicBranch != null) {
      recipients.add(magicBranch.getMailRecipients());
      reviewMessage = magicBranch.message;
      approvals.putAll(magicBranch.labels);
      Set<String> hashtags = magicBranch.hashtags;
      if (hashtags != null && !hashtags.isEmpty()) {
        hashtags.addAll(ctx.getNotes().getHashtags());
        update.setHashtags(hashtags);
      }
      if (magicBranch.topic != null
          && !magicBranch.topic.equals(ctx.getChange().getTopic())) {
        update.setTopic(magicBranch.topic);
      }
    }

    boolean draft = magicBranch != null && magicBranch.draft;
    newPatchSet = psUtil.insert(
        ctx.getDb(), ctx.getRevWalk(), update, patchSetId, commit, draft, groups,
        pushCertificate != null
          ? pushCertificate.toTextWithSignature()
          : null);

    if (checkMergedInto) {
      Ref mergedInto = findMergedInto(ctx, change.getDest().get(), commit);
      mergedIntoRef = mergedInto != null ? mergedInto.getName() : null;
    }

    recipients.add(getRecipientsFromFooters(
        accountResolver, draft, commit.getFooterLines()));
    recipients.remove(ctx.getUser().getAccountId());
    ChangeData cd = changeDataFactory.create(ctx.getDb(), ctx.getControl());
    MailRecipients oldRecipients =
        getRecipientsFromReviewers(cd.reviewers());
    approvalCopier.copy(ctx.getDb(), ctx.getControl(), newPatchSet);
    approvalsUtil.addReviewers(ctx.getDb(), update,
        projectControl.getLabelTypes(), change, newPatchSet, info,
        recipients.getReviewers(), oldRecipients.getAll());
    approvalsUtil.addApprovals(ctx.getDb(), update,
        projectControl.getLabelTypes(), newPatchSet, ctx.getControl(),
        approvals);
    recipients.add(oldRecipients);

    String approvalMessage = ApprovalsUtil.renderMessageWithApprovals(
        patchSetId.get(), approvals, scanLabels(ctx, approvals));
    StringBuilder message = new StringBuilder(approvalMessage);
    String kindMessage = changeKindMessage(changeKind);
    if (!Strings.isNullOrEmpty(kindMessage)) {
      message.append(kindMessage);
    }
    if (!Strings.isNullOrEmpty(reviewMessage)) {
      message.append("\n").append(reviewMessage);
    }
    msg = new ChangeMessage(
        new ChangeMessage.Key(change.getId(),
            ChangeUtil.messageUUID(ctx.getDb())),
        ctx.getUser().getAccountId(), ctx.getWhen(), patchSetId);
    msg.setMessage(message.toString());
    cmUtil.addChangeMessage(ctx.getDb(), update, msg);

    if (mergedIntoRef == null) {
      resetChange(ctx, msg);
    }
    ctx.saveChange();

    return true;
  }

  private String changeKindMessage(ChangeKind changeKind) {
    switch (changeKind) {
      case MERGE_FIRST_PARENT_UPDATE:
      case TRIVIAL_REBASE:
      case NO_CHANGE:
        return ": Patch Set " + priorPatchSetId.get() + " was rebased";
      case NO_CODE_CHANGE:
        return ": Commit message was updated";
      case REWORK:
      default:
        return null;
    }
  }

  private Map<String, PatchSetApproval> scanLabels(ChangeContext ctx,
      Map<String, Short> approvals) throws OrmException {
    Map<String, PatchSetApproval> current = new HashMap<>();
    // We optimize here and only retrieve current when approvals provided
    if (!approvals.isEmpty()) {
      for (PatchSetApproval a : approvalsUtil.byPatchSetUser(ctx.getDb(),
          ctx.getControl(), priorPatchSetId,
          ctx.getUser().getAccountId())) {
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

  private void resetChange(ChangeContext ctx, ChangeMessage msg)
      throws OrmException {
    Change change = ctx.getChange();
    if (change.getStatus().isClosed()) {
      ctx.getDb().patchSets().delete(Collections.singleton(newPatchSet));
      ctx.getDb().changeMessages().delete(Collections.singleton(msg));
      rejectMessage = CHANGE_IS_CLOSED;
      return;
    }

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
      change.setKey(new Change.Key("I" + commit.name()));
    } else {
      change.setKey(new Change.Key(idList.get(idList.size() - 1).trim()));
    }
  }

  @Override
  public void postUpdate(final Context ctx) throws Exception {
    if (changeKind != ChangeKind.TRIVIAL_REBASE) {
      Runnable sender = new Runnable() {
        @Override
        public void run() {
          try {
            ReplacePatchSetSender cm = replacePatchSetFactory.create(
                projectControl.getProject().getNameKey(), change.getId());
            cm.setFrom(ctx.getUser().getAccountId());
            cm.setPatchSet(newPatchSet, info);
            cm.setChangeMessage(msg);
            if (magicBranch != null && magicBranch.notify != null) {
              cm.setNotify(magicBranch.notify);
            }
            cm.addReviewers(recipients.getReviewers());
            cm.addExtraCC(recipients.getCcOnly());
            cm.send();
          } catch (Exception e) {
            log.error("Cannot send email for new patch set " + newPatchSet.getId(), e);
          }
          if (mergedIntoRef != null) {
            sendMergedEmail(ctx);
          }
        }

        @Override
        public String toString() {
          return "send-email newpatchset";
        }
      };

      if (requestScopePropagator != null) {
        sendEmailExecutor.submit(requestScopePropagator.wrap(sender));
      } else {
        sender.run();
      }
    }

    Account account = ctx.getUser().asIdentifiedUser().getAccount();
    hooks.doPatchsetCreatedHook(change, newPatchSet, ctx.getDb());
    if (mergedIntoRef != null) {
      hooks.doChangeMergedHook(change, account, newPatchSet, ctx.getDb(),
          commit.getName());
    }
    try {
      runHook(ctx);
    } catch (Exception e) {
      log.warn("ChangeHook.doCommentAddedHook delivery failed", e);
    }
  }

  private void runHook(final Context ctx) throws NoSuchChangeException,
    OrmException {
    if (approvals.isEmpty()) {
      return;
    }

    /* For labels that are not set in this operation, show the "current" value
     * of 0, and no oldValue as the value was not modified by this operation.
     * For labels that are set in this operation, the value was modified, so
     * show a transition from an oldValue of 0 to the new value.
     */
    ChangeControl changeControl = changeControlFactory.controlFor(
        ctx.getDb(), change, ctx.getUser());
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

    hooks.doCommentAddedHook(change,
        ctx.getUser().asIdentifiedUser().getAccount(), newPatchSet, null,
        allApprovals, oldApprovals, ctx.getDb());
  }

  private void sendMergedEmail(final Context ctx) {
    sendEmailExecutor.submit(requestScopePropagator.wrap(new Runnable() {
      @Override
      public void run() {
        try {
          MergedSender cm = mergedSenderFactory
              .create(projectControl.getProject().getNameKey(), change.getId());
          cm.setFrom(ctx.getUser().getAccountId());
          cm.setPatchSet(newPatchSet, info);
          cm.send();
        } catch (Exception e) {
          log.error("Cannot send email for submitted patch set "
              + patchSetId, e);
        }
      }

      @Override
      public String toString() {
        return "send-email merged";
      }
    }));
  }

  public PatchSet getPatchSet() {
    return newPatchSet;
  }

  public List<String> getGroups() {
    return groups;
  }

  public String getMergedIntoRef() {
    return mergedIntoRef;
  }

  public String getRejectMessage() {
    return rejectMessage;
  }

  private Ref findMergedInto(ChangeContext ctx, String first, RevCommit commit) {
    try {
      RefDatabase refDatabase = ctx.getRepository().getRefDatabase();

      Ref firstRef = refDatabase.exactRef(first);
      if (firstRef != null
          && isMergedInto(ctx.getRevWalk(), commit, firstRef)) {
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

  private static boolean isMergedInto(RevWalk rw, RevCommit commit, Ref ref)
      throws IOException {
    return rw.isMergedInto(commit, rw.parseCommit(ref.getObjectId()));
  }
}
