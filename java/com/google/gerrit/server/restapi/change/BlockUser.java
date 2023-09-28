// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.BlockUsers.BlockedUsersEnabled;
import com.google.gerrit.server.change.BlockUserOp;
import com.google.gerrit.server.change.DeleteReviewerByEmailOp;
import com.google.gerrit.server.change.DeleteReviewerOp;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class BlockUser implements RestModifyView<ReviewerResource, DeleteReviewerInput> {
  private final BatchUpdate.Factory updateFactory;
  private final DeleteReviewerOp.Factory deleteReviewerOpFactory;
  private final DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory;
  private final BlockUserOp.Factory blockUserFactory;

  @Inject
  BlockUser(
      BatchUpdate.Factory updateFactory,
      DeleteReviewerOp.Factory deleteReviewerOpFactory,
      DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory,
      BlockUserOp.Factory blockUserFactory) {
    this.updateFactory = updateFactory;
    this.deleteReviewerOpFactory = deleteReviewerOpFactory;
    this.deleteReviewerByEmailOpFactory = deleteReviewerByEmailOpFactory;
    this.blockUserFactory = blockUserFactory;
  }

  @Override
  public Response<Object> apply(ReviewerResource rsrc, DeleteReviewerInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    if (input == null) {
      input = new DeleteReviewerInput();
    }

    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(
              rsrc.getChangeResource().getProject(),
              rsrc.getChangeResource().getUser(),
              TimeUtil.now())) {
        if (rsrc.isByEmail()) {
          bu.addOp(
              rsrc.getChangeId(), deleteReviewerByEmailOpFactory.create(rsrc.getReviewerByEmail()));
        } else {
          DeleteReviewerOp byReviewerOp =
              deleteReviewerOpFactory.create(rsrc.getReviewerUser().getAccount(), input);
          byReviewerOp.suppressEmail();
          bu.addOp(rsrc.getChangeId(), byReviewerOp);
          bu.addOp(
              rsrc.getChangeId(), blockUserFactory.create(rsrc.getReviewerUser().getAccount()));
        }
        bu.execute();
      }
    }

    return Response.none();
  }

  @Singleton
  public static class BlockUserProvider
      implements Provider<RestModifyView<ReviewerResource, DeleteReviewerInput>> {

    private static final RestModifyView<ReviewerResource, DeleteReviewerInput> NO_OP =
        new RestModifyView<>() {
          @Override
          public Response<Object> apply(ReviewerResource resource, DeleteReviewerInput input)
              throws AuthException, BadRequestException, ResourceConflictException, Exception {
            throw new ResourceNotFoundException("Blocking users functionality is not enabled");
          }
        };

    private final RestModifyView<ReviewerResource, DeleteReviewerInput> blockUser;

    @Inject
    BlockUserProvider(BlockedUsersEnabled provider, BlockUser blockUser) {
      this.blockUser = provider.get() ? blockUser : NO_OP;
    }

    @Override
    public RestModifyView<ReviewerResource, DeleteReviewerInput> get() {
      return blockUser;
    }
  }
}
