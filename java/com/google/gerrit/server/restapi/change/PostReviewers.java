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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerResult;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerModifier;
import com.google.gerrit.server.change.ReviewerModifier.ReviewerModification;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostReviewers
    implements RestCollectionModifyView<ChangeResource, ReviewerResource, ReviewerInput> {
  private final BatchUpdate.Factory updateFactory;
  private final ChangeData.Factory changeDataFactory;
  private final NotifyResolver notifyResolver;
  private final ReviewerModifier reviewerModifier;

  @Inject
  PostReviewers(
      BatchUpdate.Factory updateFactory,
      ChangeData.Factory changeDataFactory,
      NotifyResolver notifyResolver,
      ReviewerModifier reviewerModifier) {
    this.updateFactory = updateFactory;
    this.changeDataFactory = changeDataFactory;
    this.notifyResolver = notifyResolver;
    this.reviewerModifier = reviewerModifier;
  }

  @Override
  public Response<ReviewerResult> apply(ChangeResource rsrc, ReviewerInput input)
      throws IOException, RestApiException, UpdateException, PermissionBackendException,
          ConfigInvalidException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    ReviewerModification modification =
        reviewerModifier.prepare(rsrc.getNotes(), rsrc.getUser(), input, true);
    if (modification.op == null) {
      return Response.withStatusCode(SC_BAD_REQUEST, modification.result);
    }
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.now())) {
        bu.setNotify(resolveNotify(rsrc, input));
        Change.Id id = rsrc.getChange().getId();
        bu.addOp(id, modification.op);
        bu.execute();
      }
    }

    // Re-read change to take into account results of the update.
    modification.gatherResults(changeDataFactory.create(rsrc.getProject(), rsrc.getId()));
    return Response.ok(modification.result);
  }

  private NotifyResolver.Result resolveNotify(ChangeResource rsrc, ReviewerInput input)
      throws BadRequestException, ConfigInvalidException, IOException {
    NotifyHandling notifyHandling = input.notify;
    if (notifyHandling == null) {
      notifyHandling =
          rsrc.getChange().isWorkInProgress() ? NotifyHandling.NONE : NotifyHandling.ALL;
    }
    return notifyResolver.resolve(notifyHandling, input.notifyDetails);
  }
}
