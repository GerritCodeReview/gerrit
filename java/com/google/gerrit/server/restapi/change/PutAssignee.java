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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SetAssigneeOp;
import com.google.gerrit.server.change.reviewer.ReviewerAdder;
import com.google.gerrit.server.change.reviewer.ReviewerAddition;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutAssignee extends RetryingRestModifyView<ChangeResource, AssigneeInput, AccountInfo>
    implements UiAction<ChangeResource> {

  private final AccountResolver accountResolver;
  private final SetAssigneeOp.Factory assigneeFactory;
  private final ReviewerAdder reviewerAdder;
  private final AccountLoader.Factory accountLoaderFactory;
  private final PermissionBackend permissionBackend;

  @Inject
  PutAssignee(
      AccountResolver accountResolver,
      SetAssigneeOp.Factory assigneeFactory,
      RetryHelper retryHelper,
      ReviewerAdder reviewerAdder,
      AccountLoader.Factory accountLoaderFactory,
      PermissionBackend permissionBackend) {
    super(retryHelper);
    this.accountResolver = accountResolver;
    this.assigneeFactory = assigneeFactory;
    this.reviewerAdder = reviewerAdder;
    this.accountLoaderFactory = accountLoaderFactory;
    this.permissionBackend = permissionBackend;
  }

  @Override
  protected AccountInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, AssigneeInput input)
      throws RestApiException, UpdateException, IOException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException {
    rsrc.permissions().check(ChangePermission.EDIT_ASSIGNEE);

    input.assignee = Strings.nullToEmpty(input.assignee).trim();
    if (input.assignee.isEmpty()) {
      throw new BadRequestException("missing assignee field");
    }

    IdentifiedUser assignee = accountResolver.resolve(input.assignee).asUniqueUser();
    try {
      permissionBackend
          .absentUser(assignee.getAccountId())
          .change(rsrc.getNotes())
          .check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new AuthException("read not permitted for " + input.assignee);
    }

    try (BatchUpdate bu =
        updateFactory.create(rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      SetAssigneeOp op = assigneeFactory.create(assignee);
      bu.addOp(rsrc.getId(), op);
      bu.addOp(rsrc.getId(), addAssigneeAsCc(rsrc, assignee));
      bu.execute();
      return accountLoaderFactory.create(true).fillOne(assignee.getAccountId());
    }
  }

  private ReviewerAddition addAssigneeAsCc(ChangeResource rsrc, IdentifiedUser assignee)
      throws NoSuchProjectException, PermissionBackendException, ConfigInvalidException,
          IOException {
    return reviewerAdder.prepare(
        rsrc.getNotes(),
        ImmutableList.of(
            ReviewerAdder.Input.forAccount(
                assignee.getAccountId(),
                ReviewerStateInternal.CC,
                ReviewerAdder.Options.forAutoAddingUsersFromOtherChange())));
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    return new UiAction.Description()
        .setLabel("Edit Assignee")
        .setVisible(rsrc.permissions().testCond(ChangePermission.EDIT_ASSIGNEE));
  }
}
