// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DeleteReviewerByEmailInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.DeleteReviewerSender;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DeleteReviewerByEmail
    implements RestModifyView<ChangeResource, DeleteReviewerByEmailInput> {
  private static final Logger log = LoggerFactory.getLogger(DeleteReviewer.class);

  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<ReviewDb> db;
  private final DeleteReviewerSender.Factory deleteReviewerSenderFactory;
  private final NotifyUtil notifyUtil;

  @Inject
  DeleteReviewerByEmail(
      BatchUpdate.Factory batchUpdateFactory,
      Provider<ReviewDb> db,
      DeleteReviewerSender.Factory deleteReviewerSenderFactory,
      NotifyUtil notifyUtil) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.db = db;
    this.deleteReviewerSenderFactory = deleteReviewerSenderFactory;
    this.notifyUtil = notifyUtil;
  }

  @Override
  public Response<Void> apply(ChangeResource rsrc, DeleteReviewerByEmailInput in)
      throws RestApiException, UpdateException, OrmException {
    // Don't check if reviewer.enableByEmail is true for this project as it should still be
    // possible to remove unregistered CCs even if the feature was disabled since they were added.
    if (Strings.isNullOrEmpty(in.reviewer)) {
      throw new BadRequestException("reviewer is required");
    }
    Address adr;
    try {
      adr = Address.parse(in.reviewer);
    } catch (IllegalArgumentException e) {
      throw new UnprocessableEntityException("email invalid");
    }
    if (!rsrc.getNotes().getReviewersByEmail().all().contains(adr)) {
      throw new UnprocessableEntityException("did not match any reviewer by email");
    }

    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db.get(),
            rsrc.getChange().getProject(),
            rsrc.getControl().getUser(),
            TimeUtil.nowTs())) {
      bu.addOp(rsrc.getChange().getId(), new Op(adr, in.notify, in.notifyDetails));
      bu.execute();
      return Response.none();
    }
  }

  private class Op implements BatchUpdateOp {
    private final Address address;
    public final NotifyHandling notify;
    public final Map<RecipientType, NotifyInfo> notifyDetails;

    private ChangeMessage changeMessage;
    private Change.Id changeId;

    Op(Address address, NotifyHandling notify, Map<RecipientType, NotifyInfo> notifyDetails) {
      this.address = address;
      this.notify = notify;
      this.notifyDetails = notifyDetails;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws OrmException {
      changeId = ctx.getChange().getId();
      PatchSet.Id psId = ctx.getChange().currentPatchSetId();
      String msg = "Removed reviewer " + address;
      changeMessage =
          new ChangeMessage(
              new ChangeMessage.Key(changeId, ChangeUtil.messageUuid()),
              ctx.getAccountId(),
              ctx.getWhen(),
              psId);
      changeMessage.setMessage(msg);

      ctx.getUpdate(psId).setChangeMessage(msg);
      ctx.getUpdate(psId).removeReviewerByEmail(address);
      return true;
    }

    @Override
    public void postUpdate(Context ctx) {
      if (!NotifyUtil.shouldNotify(notify, notifyDetails)) {
        return;
      }
      try {
        DeleteReviewerSender cm = deleteReviewerSenderFactory.create(ctx.getProject(), changeId);
        cm.setFrom(ctx.getAccountId());
        cm.addReviewersByEmail(Collections.singleton(address));
        cm.setChangeMessage(changeMessage.getMessage(), changeMessage.getWrittenOn());
        cm.setNotify(notify);
        cm.setAccountsToNotify(notifyUtil.resolveAccounts(notifyDetails));
        cm.send();
      } catch (Exception err) {
        log.error("Cannot email update for change " + changeId, err);
      }
    }
  }
}
