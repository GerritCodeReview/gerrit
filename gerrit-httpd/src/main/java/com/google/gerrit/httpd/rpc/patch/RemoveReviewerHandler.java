// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.patch.RemoveReviewer;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

/**
 * Implement the remote logic that removes a reviewer from a change.
 */
class RemoveReviewerHandler extends Handler<ReviewerResult> {
  interface Factory {
    RemoveReviewerHandler create(Change.Id changeId, Account.Id reviewerId);
  }

  private final RemoveReviewer.Factory removeReviewerFactory;
  private final Account.Id reviewerId;
  private final Change.Id changeId;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  @Inject
  RemoveReviewerHandler(final RemoveReviewer.Factory removeReviewerFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted Change.Id changeId, @Assisted Account.Id reviewerId) {
    this.removeReviewerFactory = removeReviewerFactory;
    this.changeId = changeId;
    this.reviewerId = reviewerId;
    this.changeDetailFactory = changeDetailFactory;
  }

  @Override
  public ReviewerResult call() throws Exception {
    ReviewerResult result = removeReviewerFactory.create(
        changeId, Collections.singleton(reviewerId)).call();
    result.setChange(changeDetailFactory.create(changeId).call());
    return result;
  }

}
