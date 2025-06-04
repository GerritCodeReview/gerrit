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

package com.google.gerrit.server.restapi.change.reviewers;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.change.ReviewerResource.REVIEWER_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.reviewers.votes.ChangeReviewersVotesRestApiModule;

public class ChangeReviewersRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Reviewers.class);

    DynamicMap.mapOf(binder(), REVIEWER_KIND);

    child(CHANGE_KIND, "reviewers").to(Reviewers.class);
    postOnCollection(REVIEWER_KIND).to(PostReviewers.class);
    delete(REVIEWER_KIND).to(DeleteReviewer.class);
    get(REVIEWER_KIND).to(GetReviewer.class);
    post(REVIEWER_KIND, "delete").to(DeleteReviewer.class);

    /** Module for {@code /changes/<account-id>/revisions/votes}. */
    install(new ChangeReviewersVotesRestApiModule());
  }
}
