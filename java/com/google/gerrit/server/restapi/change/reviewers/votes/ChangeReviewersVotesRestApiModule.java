// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change.reviewers.votes;

import static com.google.gerrit.server.change.ReviewerResource.REVIEWER_KIND;
import static com.google.gerrit.server.change.VoteResource.VOTE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class ChangeReviewersVotesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Votes.class);

    DynamicMap.mapOf(binder(), VOTE_KIND);

    child(REVIEWER_KIND, "votes").to(Votes.class);
    delete(VOTE_KIND).to(DeleteVote.class);
    post(VOTE_KIND, "delete").to(DeleteVote.class);
  }
}
