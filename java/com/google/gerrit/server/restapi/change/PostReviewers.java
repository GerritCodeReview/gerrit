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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ReviewerAdder;
import com.google.gerrit.server.change.ReviewerAdder.ReviewerAddition;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestCollectionModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostReviewers
    extends RetryingRestCollectionModifyView<
        ChangeResource, ReviewerResource, AddReviewerInput, AddReviewerResult> {

  private final Provider<ReviewDb> dbProvider;
  private final ChangeData.Factory changeDataFactory;
  private final ReviewerAdder reviewerAdder;

  @Inject
  PostReviewers(
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      RetryHelper retryHelper,
      ReviewerAdder reviewerAdder) {
    super(retryHelper);
    this.dbProvider = db;
    this.changeDataFactory = changeDataFactory;
    this.reviewerAdder = reviewerAdder;
  }

  @Override
  protected AddReviewerResult applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, AddReviewerInput input)
      throws IOException, OrmException, RestApiException, UpdateException,
          PermissionBackendException, ConfigInvalidException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    ReviewerAddition addition =
        reviewerAdder.prepare(dbProvider.get(), rsrc.getNotes(), rsrc.getUser(), input, true);
    if (addition.op == null) {
      return addition.result;
    }
    try (BatchUpdate bu =
        updateFactory.create(
            dbProvider.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Change.Id id = rsrc.getChange().getId();
      bu.addOp(id, addition.op);
      bu.execute();
    }

    // Re-read change to take into account results of the update.
    addition.gatherResults(
        changeDataFactory.create(dbProvider.get(), rsrc.getProject(), rsrc.getId()));
    return addition.result;
  }
}
