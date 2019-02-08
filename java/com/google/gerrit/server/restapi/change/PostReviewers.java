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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.reviewer.AddReviewerResultJson;
import com.google.gerrit.server.change.reviewer.AddReviewersEmailOp;
import com.google.gerrit.server.change.reviewer.ReviewerAdder;
import com.google.gerrit.server.change.reviewer.ReviewerAddition;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestCollectionModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostReviewers
    extends RetryingRestCollectionModifyView<
        ChangeResource, ReviewerResource, AddReviewerInput, AddReviewerResult> {

  private final AddReviewersEmailOp.Factory emailOpFactory;
  private final AddReviewerResultJson json;
  private final NotifyResolver notifyResolver;
  private final ReviewerAdder reviewerAdder;

  @Inject
  PostReviewers(
      AddReviewersEmailOp.Factory emailOpFactory,
      AddReviewerResultJson json,
      RetryHelper retryHelper,
      NotifyResolver notifyResolver,
      ReviewerAdder reviewerAdder) {
    super(retryHelper);
    this.emailOpFactory = emailOpFactory;
    this.json = json;
    this.notifyResolver = notifyResolver;
    this.reviewerAdder = reviewerAdder;
  }

  @Override
  protected AddReviewerResult applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, AddReviewerInput input)
      throws IOException, RestApiException, UpdateException, PermissionBackendException,
          ConfigInvalidException, NoSuchProjectException {
    ReviewerAddition addition =
        reviewerAdder.prepare(
            rsrc.getNotes(),
            ImmutableList.of(
                ReviewerAdder.Input.fromJson(input, ReviewerAdder.Options.forRestApi())));

    try (BatchUpdate bu =
        updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      bu.addOp(rsrc.getId(), addition);
      bu.addOp(rsrc.getId(), emailOpFactory.create(rsrc.getProject(), rsrc.getId(), addition));
      bu.setNotify(resolveNotify(rsrc, input));
      bu.execute();
    }

    ImmutableList<ReviewerAdder.Result> results = addition.getResults();
    checkState(results.size() == 1, "expected 1 result: %s", results);
    return json.reloadAndFormat(results.get(0), rsrc.getNotes());
  }

  private NotifyResolver.Result resolveNotify(ChangeResource rsrc, AddReviewerInput input)
      throws BadRequestException, ConfigInvalidException, IOException {
    NotifyHandling notifyHandling = input.notify;
    if (notifyHandling == null) {
      notifyHandling =
          rsrc.getChange().isWorkInProgress() ? NotifyHandling.NONE : NotifyHandling.ALL;
    }
    return notifyResolver.resolve(notifyHandling, input.notifyDetails);
  }
}
