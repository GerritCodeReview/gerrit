// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.AddToAttentionSetOp;
import com.google.gerrit.server.change.AttentionSetUnchangedOp;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.VoteResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteVote implements RestModifyView<VoteResource, DeleteVoteInput> {
  private final BatchUpdate.Factory updateFactory;
  private final NotifyResolver notifyResolver;

  private final AddToAttentionSetOp.Factory attentionSetOpFactory;
  private final Provider<CurrentUser> currentUserProvider;
  private final DeleteVoteOp.Factory deleteVoteOpFactory;

  @Inject
  DeleteVote(
      BatchUpdate.Factory updateFactory,
      NotifyResolver notifyResolver,
      AddToAttentionSetOp.Factory attentionSetOpFactory,
      Provider<CurrentUser> currentUserProvider,
      DeleteVoteOp.Factory deleteVoteOpFactory) {
    this.updateFactory = updateFactory;
    this.notifyResolver = notifyResolver;
    this.attentionSetOpFactory = attentionSetOpFactory;
    this.currentUserProvider = currentUserProvider;
    this.deleteVoteOpFactory = deleteVoteOpFactory;
  }

  @Override
  public Response<Object> apply(VoteResource rsrc, DeleteVoteInput input)
      throws RestApiException, UpdateException, IOException, ConfigInvalidException {
    if (input == null) {
      input = new DeleteVoteInput();
    }
    if (input.label != null && !rsrc.getLabel().equals(input.label)) {
      throw new BadRequestException("label must match URL");
    }
    if (input.notify == null) {
      input.notify = NotifyHandling.ALL;
    }
    ReviewerResource r = rsrc.getReviewer();
    Change change = r.getChange();

    if (r.getRevisionResource() != null && !r.getRevisionResource().isCurrent()) {
      throw new MethodNotAllowedException("Cannot delete vote on non-current patch set");
    }

    try (BatchUpdate bu =
        updateFactory.create(
            change.getProject(), r.getChangeResource().getUser(), TimeUtil.now())) {
      bu.setNotify(
          notifyResolver.resolve(
              firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails));
      bu.addOp(
          change.getId(),
          deleteVoteOpFactory.create(
              r.getChange().getProject(),
              r.getReviewerUser().state(),
              rsrc.getLabel(),
              input,
              true));
      if (!input.ignoreAutomaticAttentionSetRules
          && !r.getReviewerUser().getAccountId().equals(currentUserProvider.get().getAccountId())) {
        bu.addOp(
            change.getId(),
            attentionSetOpFactory.create(
                r.getReviewerUser().getAccountId(),
                /* reason= */ "Their vote was deleted",
                /* notify= */ false));
      }
      if (input.ignoreAutomaticAttentionSetRules) {
        bu.addOp(change.getId(), new AttentionSetUnchangedOp());
      }
      bu.execute();
    }

    return Response.none();
  }
}
