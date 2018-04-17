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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SetAssigneeOp;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.gerrit.server.restapi.change.PostReviewers.Addition;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutAssignee extends RetryingRestModifyView<ChangeResource, AssigneeInput, AccountInfo>
    implements UiAction<ChangeResource> {

  private final AccountsCollection accounts;
  private final SetAssigneeOp.Factory assigneeFactory;
  private final Provider<ReviewDb> db;
  private final PostReviewers postReviewers;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  PutAssignee(
      AccountsCollection accounts,
      SetAssigneeOp.Factory assigneeFactory,
      RetryHelper retryHelper,
      Provider<ReviewDb> db,
      PostReviewers postReviewers,
      AccountLoader.Factory accountLoaderFactory) {
    super(retryHelper);
    this.accounts = accounts;
    this.assigneeFactory = assigneeFactory;
    this.db = db;
    this.postReviewers = postReviewers;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  protected AccountInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, AssigneeInput input)
      throws RestApiException, UpdateException, OrmException, IOException,
          PermissionBackendException, ConfigInvalidException, NoSuchProjectException {
    rsrc.permissions().check(ChangePermission.EDIT_ASSIGNEE);

    input.assignee = Strings.nullToEmpty(input.assignee).trim();
    if (input.assignee.isEmpty()) {
      throw new BadRequestException("missing assignee field");
    }

    IdentifiedUser assignee = accounts.parse(input.assignee);
    if (!assignee.getAccount().isActive()) {
      throw new UnprocessableEntityException(input.assignee + " is not active");
    }
    try {
      rsrc.permissions().database(db).user(assignee).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new AuthException("read not permitted for " + input.assignee);
    }

    try (BatchUpdate bu =
        updateFactory.create(
            db.get(), rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      SetAssigneeOp op = assigneeFactory.create(assignee);
      bu.addOp(rsrc.getId(), op);

      PostReviewers.Addition reviewersAddition = addAssigneeAsCC(rsrc, input.assignee);
      bu.addOp(rsrc.getId(), reviewersAddition.op);

      bu.execute();
      return accountLoaderFactory.create(true).fillOne(assignee.getAccountId());
    }
  }

  private Addition addAssigneeAsCC(ChangeResource rsrc, String assignee)
      throws OrmException, IOException, PermissionBackendException, ConfigInvalidException,
          NoSuchProjectException {
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
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_ASSIGNEE));
  }
}
