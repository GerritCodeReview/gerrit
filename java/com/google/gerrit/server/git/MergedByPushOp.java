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

import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.FROM_ID;
import static com.google.gerrit.proto.Entities.EmailTask.Header.HeaderName.MESSAGE_ID;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.entities.converter.ChangeIdProtoConverter;
import com.google.gerrit.entities.converter.PatchSetIdProtoConverter;
import com.google.gerrit.entities.converter.ProjectNameKeyProtoConverter;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.ChangeMerged;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Operation to close a change on push.
 *
 * <p>When we find a change corresponding to a commit that is pushed to a branch directly, we close
 * the change. This class marks the change as merged, and sends out the email notification.
 */
public class MergedByPushOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    MergedByPushOp create(
        RequestScopePropagator requestScopePropagator,
        PatchSet.Id psId,
        @Assisted SubmissionId submissionId,
        @Assisted("refName") String refName,
        @Assisted("mergeResultRevId") String mergeResultRevId);
  }

  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeMessagesUtil cmUtil;
  private final PatchSetUtil psUtil;
  private final ChangeMerged changeMerged;
  private final MessageIdGenerator messageIdGenerator;
  private final EmailTaskDispatcher emailTaskDispatcher;

  private final PatchSet.Id psId;
  private final SubmissionId submissionId;
  private final String refName;
  private final String mergeResultRevId;

  private Change change;
  private boolean correctBranch;
  private Provider<PatchSet> patchSetProvider;
  private PatchSet patchSet;
  private PatchSetInfo info;

  @Inject
  MergedByPushOp(
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeMessagesUtil cmUtil,
      PatchSetUtil psUtil,
      ChangeMerged changeMerged,
      MessageIdGenerator messageIdGenerator,
      EmailTaskDispatcher emailTaskDispatcher,
      @Assisted PatchSet.Id psId,
      @Assisted SubmissionId submissionId,
      @Assisted("refName") String refName,
      @Assisted("mergeResultRevId") String mergeResultRevId) {
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.cmUtil = cmUtil;
    this.psUtil = psUtil;
    this.changeMerged = changeMerged;
    this.messageIdGenerator = messageIdGenerator;
    this.emailTaskDispatcher = emailTaskDispatcher;
    this.submissionId = submissionId;
    this.psId = psId;
    this.refName = refName;
    this.mergeResultRevId = mergeResultRevId;
  }

  public String getMergedIntoRef() {
    return refName;
  }

  public MergedByPushOp setPatchSetProvider(Provider<PatchSet> patchSetProvider) {
    this.patchSetProvider = requireNonNull(patchSetProvider);
    return this;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws IOException {
    change = ctx.getChange();
    correctBranch = refName.equals(change.getDest().branch());
    if (!correctBranch) {
      return false;
    }

    if (patchSetProvider != null) {
      // Caller might have also arranged for construction of a new patch set
      // that is not present in the old notes so we can't use PatchSetUtil.
      patchSet = patchSetProvider.get();
    } else {
      patchSet =
          requireNonNull(
              psUtil.get(ctx.getNotes(), psId),
              () -> String.format("patch set %s not found", psId));
    }
    info = getPatchSetInfo(ctx);

    ChangeUpdate update = ctx.getUpdate(psId);
    if (change.isMerged()) {
      return true;
    }
    change.setCurrentPatchSet(info);
    change.setStatus(Change.Status.MERGED);
    change.setSubmissionId(submissionId.toString());
    // we cannot reconstruct the submit records for when this change was
    // submitted, this is why we must fix the status and other details.
    update.fixStatusToMerged(submissionId);
    update.setCurrentPatchSet();
    if (change.isWorkInProgress()) {
      change.setWorkInProgress(false);
      update.setWorkInProgress(false);
    }
    StringBuilder msgBuf = new StringBuilder();
    msgBuf.append("Change has been successfully pushed");
    if (!refName.equals(change.getDest().branch())) {
      msgBuf.append(" into ");
      if (refName.startsWith(Constants.R_HEADS)) {
        msgBuf.append("branch ");
        msgBuf.append(Repository.shortenRefName(refName));
      } else {
        msgBuf.append(refName);
      }
    }
    msgBuf.append(".");
    cmUtil.setChangeMessage(update, msgBuf.toString(), ChangeMessagesUtil.TAG_MERGED);
    update.putApproval(LabelId.legacySubmit().get(), (short) 1);
    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (!correctBranch) {
      return;
    }
    try {
      String messageId = messageIdGenerator.fromChangeUpdate(ctx.getRepoView(), patchSet.id()).id();
      EmailTask.Builder emailTaskBuilder =
          EmailTask.newBuilder()
              .setEventType(EmailTask.Type.MERGED)
              .setProject(ProjectNameKeyProtoConverter.INSTANCE.toProto(ctx.getProject()))
              .setChangeId(ChangeIdProtoConverter.INSTANCE.toProto(change.getId()))
              .setPatchsetId(PatchSetIdProtoConverter.INSTANCE.toProto(patchSet.id()))
              .addHeader(header(MESSAGE_ID, messageId))
              .addHeader(header(FROM_ID, ctx.getAccountId().toString()));
      emailTaskDispatcher.dispatch(emailTaskBuilder.build());
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Cannot send email for submitted patch set %s", psId);
    }
    changeMerged.fire(
        ctx.getChangeData(change), patchSet, ctx.getAccount(), mergeResultRevId, ctx.getWhen());
  }

  private EmailTask.Header header(EmailTask.Header.HeaderName headerName, String value) {
    return EmailTask.Header.newBuilder().setName(headerName).setValue(value).build();
  }

  private PatchSetInfo getPatchSetInfo(ChangeContext ctx) throws IOException {
    RevWalk rw = ctx.getRevWalk();
    RevCommit commit = rw.parseCommit(requireNonNull(patchSet).commitId());
    return patchSetInfoFactory.get(rw, commit, psId);
  }
}
