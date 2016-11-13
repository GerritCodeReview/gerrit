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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.PublishDraftPatchSet.Input;
import com.google.gerrit.server.extensions.events.DraftPublished;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.send.CreateChangeSender;
import com.google.gerrit.server.mail.send.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PublishDraftPatchSet
    implements RestModifyView<RevisionResource, Input>, UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(PublishDraftPatchSet.class);

  public static class Input {}

  private final AccountResolver accountResolver;
  private final ApprovalsUtil approvalsUtil;
  private final BatchUpdate.Factory updateFactory;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final Provider<ReviewDb> dbProvider;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final DraftPublished draftPublished;

  @Inject
  public PublishDraftPatchSet(
      AccountResolver accountResolver,
      ApprovalsUtil approvalsUtil,
      BatchUpdate.Factory updateFactory,
      CreateChangeSender.Factory createChangeSenderFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      Provider<ReviewDb> dbProvider,
      ReplacePatchSetSender.Factory replacePatchSetFactory,
      DraftPublished draftPublished) {
    this.accountResolver = accountResolver;
    this.approvalsUtil = approvalsUtil;
    this.updateFactory = updateFactory;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.psUtil = psUtil;
    this.dbProvider = dbProvider;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.draftPublished = draftPublished;
  }

  @Override
  public Response<?> apply(RevisionResource rsrc, Input input)
      throws RestApiException, UpdateException {
    return apply(rsrc.getUser(), rsrc.getChange(), rsrc.getPatchSet().getId(), rsrc.getPatchSet());
  }

  private Response<?> apply(CurrentUser u, Change c, PatchSet.Id psId, PatchSet ps)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu =
        updateFactory.create(dbProvider.get(), c.getProject(), u, TimeUtil.nowTs())) {
      bu.addOp(c.getId(), new Op(psId, ps));
      bu.execute();
    }
    return Response.none();
  }

  @Override
  public UiAction.Description getDescription(RevisionResource rsrc) {
    try {
      return new UiAction.Description()
          .setLabel("Publish")
          .setTitle(String.format("Publish revision %d", rsrc.getPatchSet().getPatchSetId()))
          .setVisible(
              rsrc.getPatchSet().isDraft() && rsrc.getControl().canPublish(dbProvider.get()));
    } catch (OrmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static class CurrentRevision implements RestModifyView<ChangeResource, Input> {
    private final PublishDraftPatchSet publish;

    @Inject
    CurrentRevision(PublishDraftPatchSet publish) {
      this.publish = publish;
    }

    @Override
    public Response<?> apply(ChangeResource rsrc, Input input)
        throws RestApiException, UpdateException {
      return publish.apply(
          rsrc.getControl().getUser(),
          rsrc.getChange(),
          rsrc.getChange().currentPatchSetId(),
          null);
    }
  }

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;

    private PatchSet patchSet;
    private Change change;
    private boolean wasDraftChange;
    private PatchSetInfo patchSetInfo;
    private MailRecipients recipients;

    private Op(PatchSet.Id psId, @Nullable PatchSet patchSet) {
      this.psId = psId;
      this.patchSet = patchSet;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException {
      if (!ctx.getControl().canPublish(ctx.getDb())) {
        throw new AuthException("Cannot publish this draft patch set");
      }
      if (patchSet == null) {
        patchSet = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
        if (patchSet == null) {
          throw new ResourceNotFoundException(psId.toString());
        }
      }
      saveChange(ctx);
      savePatchSet(ctx);
      addReviewers(ctx);
      return true;
    }

    private void saveChange(ChangeContext ctx) {
      change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(psId);
      wasDraftChange = change.getStatus() == Change.Status.DRAFT;
      if (wasDraftChange) {
        change.setStatus(Change.Status.NEW);
        update.setStatus(change.getStatus());
      }
    }

    private void savePatchSet(ChangeContext ctx) throws RestApiException, OrmException {
      if (!patchSet.isDraft()) {
        throw new ResourceConflictException("Patch set is not a draft");
      }
      psUtil.publish(ctx.getDb(), ctx.getUpdate(psId), patchSet);
    }

    private void addReviewers(ChangeContext ctx) throws OrmException, IOException {
      LabelTypes labelTypes = ctx.getControl().getLabelTypes();
      Collection<Account.Id> oldReviewers =
          approvalsUtil.getReviewers(ctx.getDb(), ctx.getNotes()).all();
      RevCommit commit =
          ctx.getRevWalk().parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
      patchSetInfo = patchSetInfoFactory.get(ctx.getRevWalk(), commit, psId);

      List<FooterLine> footerLines = commit.getFooterLines();
      recipients =
          getRecipientsFromFooters(ctx.getDb(), accountResolver, patchSet.isDraft(), footerLines);
      recipients.remove(ctx.getAccountId());
      approvalsUtil.addReviewers(
          ctx.getDb(),
          ctx.getUpdate(psId),
          labelTypes,
          change,
          patchSet,
          patchSetInfo,
          recipients.getReviewers(),
          oldReviewers);
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      draftPublished.fire(change, patchSet, ctx.getAccount(), ctx.getWhen());
      if (patchSet.isDraft() && change.getStatus() == Change.Status.DRAFT) {
        // Skip emails if the patch set is still a draft.
        return;
      }
      try {
        if (wasDraftChange) {
          sendCreateChange(ctx);
        } else {
          sendReplacePatchSet(ctx);
        }
      } catch (EmailException | OrmException e) {
        log.error("Cannot send email for publishing draft " + psId, e);
      }
    }

    private void sendCreateChange(Context ctx) throws EmailException {
      CreateChangeSender cm = createChangeSenderFactory.create(ctx.getProject(), change.getId());
      cm.setFrom(ctx.getAccountId());
      cm.setPatchSet(patchSet, patchSetInfo);
      cm.addReviewers(recipients.getReviewers());
      cm.addExtraCC(recipients.getCcOnly());
      cm.send();
    }

    private void sendReplacePatchSet(Context ctx) throws EmailException, OrmException {
      ChangeMessage msg =
          ChangeMessagesUtil.newMessage(
              ctx.getDb(),
              psId,
              ctx.getUser(),
              ctx.getWhen(),
              "Uploaded patch set " + psId.get() + ".",
              ChangeMessagesUtil.TAG_UPLOADED_PATCH_SET);
      ReplacePatchSetSender cm = replacePatchSetFactory.create(ctx.getProject(), change.getId());
      cm.setFrom(ctx.getAccountId());
      cm.setPatchSet(patchSet, patchSetInfo);
      cm.setChangeMessage(msg.getMessage(), ctx.getWhen());
      cm.addReviewers(recipients.getReviewers());
      cm.addExtraCC(recipients.getCcOnly());
      cm.send();
    }
  }
}
