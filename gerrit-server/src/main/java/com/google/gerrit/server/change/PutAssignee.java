// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountJson;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class PutAssignee
    implements RestModifyView<ChangeResource, AssigneeInput> {

  private final SetAssigneeOp.Factory assigneeFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<ReviewDb> db;

  @Inject
  PutAssignee(SetAssigneeOp.Factory assigneeFactory,
      BatchUpdate.Factory batchUpdateFactory,
      Provider<ReviewDb> db) {
    this.assigneeFactory = assigneeFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.db = db;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource rsrc, AssigneeInput input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu = batchUpdateFactory.create(db.get(),
        rsrc.getChange().getProject(), rsrc.getControl().getUser(),
        TimeUtil.nowTs())) {
      SetAssigneeOp op = assigneeFactory.create(input);
      bu.addOp(rsrc.getId(), op);
      bu.execute();
      return Response.ok(AccountJson.toAccountInfo(op.getNewAssignee()));
    }
  }
}
