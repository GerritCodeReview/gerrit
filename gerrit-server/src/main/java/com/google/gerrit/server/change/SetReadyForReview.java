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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.WorkInProgressOp.Input;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class SetReadyForReview
    implements RestModifyView<ChangeResource, Input>, UiAction<ChangeResource> {
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> db;

  @Inject
  SetReadyForReview(
      BatchUpdate.Factory batchUpdateFactory, ChangeMessagesUtil cmUtil, Provider<ReviewDb> db) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.cmUtil = cmUtil;
    this.db = db;
  }

  @Override
  public Response<?> apply(ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException {
    Change change = rsrc.getChange();
    if (change.getStatus() != Status.NEW) {
      throw new ResourceConflictException("change is " + ChangeUtil.status(change));
    }

    if (!change.isWip()) {
      throw new ResourceConflictException("change is not work in progress");
    }

    try (BatchUpdate bu =
        batchUpdateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      bu.addOp(rsrc.getChange().getId(), new WorkInProgressOp(cmUtil, false, input));
      bu.execute();
      return Response.none();
    }
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    return new Description()
        .setLabel("Ready")
        .setTitle("Set Ready For Review")
        .setVisible(
            rsrc.getControl().isOwner()
                && rsrc.getChange().getStatus() == Status.NEW
                && rsrc.getChange().isWip());
  }
}
