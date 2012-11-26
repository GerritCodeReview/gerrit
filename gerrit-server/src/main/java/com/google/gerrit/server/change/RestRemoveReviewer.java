// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.RestRemoveReviewer.Input;
import com.google.gerrit.server.patch.RemoveReviewer;
import com.google.inject.Inject;

import java.util.Collections;

class RestRemoveReviewer implements RestModifyView<ReviewerResource, Input> {
  static class Input {
  }

  private final RemoveReviewer.Factory removeReviewerFactory;

  @Inject
  RestRemoveReviewer(RemoveReviewer.Factory removeReviewerFactory) {
    this.removeReviewerFactory = removeReviewerFactory;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(ReviewerResource resource, Input input)
      throws Exception {
    Change.Id changeId = resource.getChange().getId();
    Account.Id accountId = resource.getAccount().getId();
    removeReviewerFactory.create(
        changeId, Collections.singleton(accountId)).call();
    return null;
  }
}
