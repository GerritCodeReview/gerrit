// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.changedetail.ChangeDetailFactory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.patch.AddReviewer;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;

class AddReviewerHandler extends Handler<ReviewerResult> {
  interface Factory {
    AddReviewerHandler create(Change.Id changeId,
        Collection<String> userNameOrEmailOrGroupNames, boolean confirmed);
  }

  private final AddReviewer.Factory addReviewerFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final Change.Id changeId;
  private final Collection<String> reviewers;
  private final boolean confirmed;

  @Inject
  AddReviewerHandler(final AddReviewer.Factory addReviewerFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final Change.Id changeId,
      @Assisted final Collection<String> userNameOrEmailOrGroupNames,
      @Assisted final boolean confirmed) {

    this.addReviewerFactory = addReviewerFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.changeId = changeId;
    this.reviewers = userNameOrEmailOrGroupNames;
    this.confirmed = confirmed;
  }

  @Override
  public ReviewerResult call() throws Exception {
    ReviewerResult result = addReviewerFactory.create(changeId, reviewers, confirmed).call();
    result.setChange(changeDetailFactory.create(changeId).call());
    return result;
  }
}
