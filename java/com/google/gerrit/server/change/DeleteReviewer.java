// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteReviewer
    extends RetryingRestModifyView<ReviewerResource, DeleteReviewerInput, Response<?>> {

  private final Provider<ReviewDb> dbProvider;
  private final DeleteReviewerOp.Factory deleteReviewerOpFactory;
  private final DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory;

  @Inject
  DeleteReviewer(
      Provider<ReviewDb> dbProvider,
      RetryHelper retryHelper,
      DeleteReviewerOp.Factory deleteReviewerOpFactory,
      DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.deleteReviewerOpFactory = deleteReviewerOpFactory;
    this.deleteReviewerByEmailOpFactory = deleteReviewerByEmailOpFactory;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, ReviewerResource rsrc, DeleteReviewerInput input)
      throws RestApiException, UpdateException {
    if (input == null) {
      input = new DeleteReviewerInput();
    }

    try (BatchUpdate bu =
        updateFactory.create(
            dbProvider.get(),
            rsrc.getChangeResource().getProject(),
            rsrc.getChangeResource().getUser(),
            TimeUtil.nowTs())) {
      BatchUpdateOp op;
      if (rsrc.isByEmail()) {
        op = deleteReviewerByEmailOpFactory.create(rsrc.getReviewerByEmail(), input);
      } else {
        op = deleteReviewerOpFactory.create(rsrc.getReviewerUser().getAccount(), input);
      }
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
    }
    return Response.none();
  }
}
