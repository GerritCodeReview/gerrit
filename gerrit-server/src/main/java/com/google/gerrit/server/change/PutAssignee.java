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

package com.google.gerrit.server.change;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.PostReviewers.Addition;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class PutAssignee
    implements RestModifyView<ChangeResource, AssigneeInput>, UiAction<ChangeResource> {

  private final SetAssigneeOp.Factory assigneeFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<ReviewDb> db;
  private final PostReviewers postReviewers;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  PutAssignee(
      SetAssigneeOp.Factory assigneeFactory,
      BatchUpdate.Factory batchUpdateFactory,
      Provider<ReviewDb> db,
      PostReviewers postReviewers,
      AccountLoader.Factory accountLoaderFactory) {
    this.assigneeFactory = assigneeFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.db = db;
    this.postReviewers = postReviewers;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource rsrc, AssigneeInput input)
      throws RestApiException, UpdateException, OrmException, IOException,
          PermissionBackendException {
    rsrc.permissions().check(ChangePermission.EDIT_ASSIGNEE);

    if (input.assignee == null || input.assignee.trim().isEmpty()) {
      throw new BadRequestException("missing assignee field");
    }

    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db.get(),
            rsrc.getChange().getProject(),
            rsrc.getControl().getUser(),
            TimeUtil.nowTs())) {
      SetAssigneeOp op = assigneeFactory.create(input.assignee);
      bu.addOp(rsrc.getId(), op);

      PostReviewers.Addition reviewersAddition = addAssigneeAsCC(rsrc, input.assignee);
      bu.addOp(rsrc.getId(), reviewersAddition.op);

      bu.execute();
      return Response.ok(accountLoaderFactory.create(true).fillOne(op.getNewAssignee()));
    }
  }

  private Addition addAssigneeAsCC(ChangeResource rsrc, String assignee)
      throws OrmException, RestApiException, IOException {
    AddReviewerInput reviewerInput = new AddReviewerInput();
    reviewerInput.reviewer = assignee;
    reviewerInput.state = ReviewerState.CC;
    reviewerInput.confirmed = true;
    reviewerInput.notify = NotifyHandling.NONE;
    return postReviewers.prepareApplication(rsrc, reviewerInput, false);
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Assignee")
        .setVisible(rsrc.permissions().testOrFalse(ChangePermission.EDIT_ASSIGNEE));
  }
}
