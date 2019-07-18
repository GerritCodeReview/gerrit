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

package com.google.gerrit.server.api.changes;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.ReviewerApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.change.VoteResource;
import com.google.gerrit.server.restapi.change.DeleteReviewer;
import com.google.gerrit.server.restapi.change.DeleteVote;
import com.google.gerrit.server.restapi.change.Votes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;

public class ReviewerApiImpl implements ReviewerApi {
  interface Factory {
    ReviewerApiImpl create(ReviewerResource r);
  }

  private final ReviewerResource reviewer;
  private final Votes.List listVotes;
  private final DeleteVote deleteVote;
  private final DeleteReviewer deleteReviewer;

  @Inject
  ReviewerApiImpl(
      Votes.List listVotes,
      DeleteVote deleteVote,
      DeleteReviewer deleteReviewer,
      @Assisted ReviewerResource reviewer) {
    this.listVotes = listVotes;
    this.deleteVote = deleteVote;
    this.deleteReviewer = deleteReviewer;
    this.reviewer = reviewer;
  }

  @Override
  public Map<String, Short> votes() throws RestApiException {
    try {
      return listVotes.apply(reviewer).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list votes", e);
    }
  }

  @Override
  public void deleteVote(String label) throws RestApiException {
    try {
      deleteVote.apply(new VoteResource(reviewer, label), null);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete vote", e);
    }
  }

  @Override
  public void deleteVote(DeleteVoteInput input) throws RestApiException {
    try {
      deleteVote.apply(new VoteResource(reviewer, input.label), input);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete vote", e);
    }
  }

  @Override
  public void remove() throws RestApiException {
    remove(new DeleteReviewerInput());
  }

  @Override
  public void remove(DeleteReviewerInput input) throws RestApiException {
    try {
      deleteReviewer.apply(reviewer, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot remove reviewer", e);
    }
  }
}
