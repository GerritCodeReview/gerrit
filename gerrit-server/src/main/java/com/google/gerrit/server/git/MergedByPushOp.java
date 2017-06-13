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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.extensions.events.ChangeMerged;
import com.google.gerrit.server.mail.send.MergedSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergedByPushOp implements BatchUpdateOp {
  private static final Logger log = LoggerFactory.getLogger(MergedByPushOp.class);

  public interface Factory {
    MergedByPushOp create(
        RequestScopePropagator requestScopePropagator, PatchSet.Id psId, String refName);
  }

  private final RequestScopePropagator requestScopePropagator;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeMessagesUtil cmUtil;
  private final MergedSender.Factory mergedSenderFactory;
  private final PatchSetUtil psUtil;
  private final ExecutorService sendEmailExecutor;
  private final ChangeMerged changeMerged;

  private final PatchSet.Id psId;
  private final String refName;

  private Change change;
  private boolean correctBranch;
  private Provider<PatchSet> patchSetProvider;
  private PatchSet patchSet;
  private PatchSetInfo info;

  @Inject
  MergedByPushOp(
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeMessagesUtil cmUtil,
      MergedSender.Factory mergedSenderFactory,
      PatchSetUtil psUtil,
      @SendEmailExecutor ExecutorService sendEmailExecutor,
      ChangeMerged changeMerged,
      @Assisted RequestScopePropagator requestScopePropagator,
      @Assisted PatchSet.Id psId,
      @Assisted String refName) {
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.cmUtil = cmUtil;
    this.mergedSenderFactory = mergedSenderFactory;
    this.psUtil = psUtil;
    this.sendEmailExecutor = sendEmailExecutor;
    this.changeMerged = changeMerged;
    this.requestScopePropagator = requestScopePropagator;
    this.psId = psId;
    this.refName = refName;
  }

  public String getMergedIntoRef() {
    return refName;
  }

  public MergedByPushOp setPatchSetProvider(Provider<PatchSet> patchSetProvider) {
    this.patchSetProvider = checkNotNull(patchSetProvider);
    return this;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException, IOException {
    change = ctx.getChange();
    correctBranch = refName.equals(change.getDest().get());
    if (!correctBranch) {
      return false;
    }

    if (patchSetProvider != null) {
      // Caller might have also arranged for construction of a new patch set
      // that is not present in the old notes so we can't use PatchSetUtil.
      patchSet = patchSetProvider.get();
    } else {
      patchSet =
          checkNotNull(
              psUtil.get(ctx.getDb(), ctx.getNotes(), psId), "patch set %s not found", psId);
    }
    info = getPatchSetInfo(ctx);

    ChangeUpdate update = ctx.getUpdate(psId);
    Change.Status status = change.getStatus();
    if (status == Change.Status.MERGED) {
      return true;
    }
    if (status.isOpen()) {
      change.setCurrentPatchSet(info);
      change.setStatus(Change.Status.MERGED);

      // we cannot reconstruct the submit records for when this change was
      // submitted, this is why we must fix the status
      update.fixStatus(Change.Status.MERGED);
      update.setCurrentPatchSet();
    }

    StringBuilder msgBuf = new StringBuilder();
    msgBuf.append("Change has been successfully pushed");
    if (!refName.equals(change.getDest().get())) {
      msgBuf.append(" into ");
      if (refName.startsWith(Constants.R_HEADS)) {
        msgBuf.append("branch ");
        msgBuf.append(Repository.shortenRefName(refName));
      } else {
        msgBuf.append(refName);
      }
    }
    msgBuf.append(".");
    ChangeMessage msg =
        ChangeMessagesUtil.newMessage(
            psId, ctx.getUser(), ctx.getWhen(), msgBuf.toString(), ChangeMessagesUtil.TAG_MERGED);
    cmUtil.addChangeMessage(ctx.getDb(), update, msg);

    PatchSetApproval submitter =
        ApprovalsUtil.newApproval(
            change.currentPatchSetId(), ctx.getUser(), LabelId.legacySubmit(), 1, ctx.getWhen());
    update.putApproval(submitter.getLabel(), submitter.getValue());
    ctx.getDb().patchSetApprovals().upsert(Collections.singleton(submitter));

    return true;
  }

  @Override
  public void postUpdate(Context ctx) {
    if (!correctBranch) {
      return;
    }
    @SuppressWarnings("unused") // Runnable already handles errors
    Future<?> possiblyIgnoredError =
        sendEmailExecutor.submit(
            requestScopePropagator.wrap(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      MergedSender cm =
                          mergedSenderFactory.create(ctx.getProject(), psId.getParentKey());
                      cm.setFrom(ctx.getAccountId());
                      cm.setPatchSet(patchSet, info);
                      cm.send();
                    } catch (Exception e) {
                      log.error("Cannot send email for submitted patch set " + psId, e);
                    }
                  }

                  @Override
                  public String toString() {
                    return "send-email merged";
                  }
                }));

    changeMerged.fire(
        change, patchSet, ctx.getAccount(), patchSet.getRevision().get(), ctx.getWhen());
  }

  private PatchSetInfo getPatchSetInfo(ChangeContext ctx) throws IOException {
    RevWalk rw = ctx.getRevWalk();
    RevCommit commit =
        rw.parseCommit(ObjectId.fromString(checkNotNull(patchSet).getRevision().get()));
    return patchSetInfoFactory.get(rw, commit, psId);
  }
}
