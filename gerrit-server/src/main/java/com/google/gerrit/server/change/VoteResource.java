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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.TypeLiteral;

public class VoteResource implements RestResource {
  public static final TypeLiteral<RestView<VoteResource>> VOTE_KIND =
      new TypeLiteral<RestView<VoteResource>>() {};

  private final ReviewerResource reviewer;
  private final LabelVote vote;

  public VoteResource(ReviewerResource reviewer, String id) {
    this(reviewer, LabelVote.parse(id));
  }

  public VoteResource(ReviewerResource reviewer, LabelVote vote) {
    this.reviewer = reviewer;
    this.vote = vote;
  }

  public ReviewerResource getReviewer() {
    return reviewer;
  }

  public LabelVote getVote() {
    return vote;
  }
}
