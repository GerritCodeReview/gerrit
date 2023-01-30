// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.update.context;

import com.google.gerrit.entities.Change;

public abstract class UpdateContext implements AutoCloseable {
  public enum UpdateType {
    MERGE_CHANGE,
    INIT_ALL_PROJECTS,
    INIT_ALL_USERS,
    ACQURE_SEQ,
    CREATE_ADMINS_GROUP,
    CREATE_BATCH_USERS_GROUP,
    ACCOUNTS_UPDATE,
    GROUPS_UPDATE,
    CREATE_PROJECT,
    INSERT_CHANGES_AND_PATCH_SETS,
    DELETE_CHANGE_OP,
    DELETE_CHANGE,
    TEST_UPDATE,
    CREATE_CHANGE,
    POST_REVIEW,
  }
  private static UpdateContext open(UpdateContext ctx) {
    return UpdateContextManager.getThreadLocalInstance().open(ctx);
  }

  public static UpdateContext merge(Change.Id changeId) {
    return open(UpdateMergeContext.create(changeId));
  }

  public static UpdateContext open(UpdateType updateType) {
    return open(SimpleUpdateContext.create(updateType));
  }

  @Override
  public void close() {
    UpdateContextManager.getThreadLocalInstance().close(this);
  }

  public abstract UpdateType getUpdateType();
}


