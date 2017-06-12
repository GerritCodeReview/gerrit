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

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class PostHashtags
    implements RestModifyView<ChangeResource, HashtagsInput>, UiAction<ChangeResource> {
  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final SetHashtagsOp.Factory hashtagsFactory;

  @Inject
  PostHashtags(
      Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      SetHashtagsOp.Factory hashtagsFactory) {
    this.db = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.hashtagsFactory = hashtagsFactory;
  }

  @Override
  public Response<ImmutableSortedSet<String>> apply(ChangeResource req, HashtagsInput input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db.get(), req.getChange().getProject(), req.getControl().getUser(), TimeUtil.nowTs())) {
      SetHashtagsOp op = hashtagsFactory.create(input);
      bu.addOp(req.getId(), op);
      bu.execute();
      return Response.<ImmutableSortedSet<String>>ok(op.getUpdatedHashtags());
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
        .setLabel("Edit Hashtags")
        .setVisible(resource.getControl().canEditHashtags());
  }
}
