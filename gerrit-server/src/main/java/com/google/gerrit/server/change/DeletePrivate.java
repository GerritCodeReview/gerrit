// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeletePrivate implements RestModifyView<ChangeResource, DeletePrivate.Input> {
  public static class Input {}

  private final Provider<ReviewDb> dbProvider;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  DeletePrivate(Provider<ReviewDb> dbProvider, BatchUpdate.Factory batchUpdateFactory) {
    this.dbProvider = dbProvider;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public Response<String> apply(ChangeResource rsrc, DeletePrivate.Input input)
      throws RestApiException, UpdateException {
    if (!rsrc.getChange().isPrivate()) {
      throw new ResourceConflictException("change not private");
    }

    ChangeControl control = rsrc.getControl();
    SetPrivateOp op = new SetPrivateOp(false);
    try (BatchUpdate u =
        batchUpdateFactory.create(
            dbProvider.get(),
            control.getProject().getNameKey(),
            control.getUser(),
            TimeUtil.nowTs())) {
      u.addOp(control.getId(), op).execute();
    }

    return Response.created("");
  }
}
