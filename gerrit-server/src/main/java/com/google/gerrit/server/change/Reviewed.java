// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class Reviewed {
  public static class Input {}

  @Singleton
  public static class PutReviewed implements RestModifyView<FileResource, Input> {
    private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;

    @Inject
    PutReviewed(DynamicItem<AccountPatchReviewStore> accountPatchReviewStore) {
      this.accountPatchReviewStore = accountPatchReviewStore;
    }

    @Override
    public Response<String> apply(FileResource resource, Input input) throws OrmException {
      if (accountPatchReviewStore
          .get()
          .markReviewed(
              resource.getPatchKey().getParentKey(),
              resource.getAccountId(),
              resource.getPatchKey().getFileName())) {
        return Response.created("");
      }
      return Response.ok("");
    }
  }

  @Singleton
  public static class DeleteReviewed implements RestModifyView<FileResource, Input> {
    private final DynamicItem<AccountPatchReviewStore> accountPatchReviewStore;

    @Inject
    DeleteReviewed(DynamicItem<AccountPatchReviewStore> accountPatchReviewStore) {
      this.accountPatchReviewStore = accountPatchReviewStore;
    }

    @Override
    public Response<?> apply(FileResource resource, Input input) throws OrmException {
      accountPatchReviewStore
          .get()
          .clearReviewed(
              resource.getPatchKey().getParentKey(),
              resource.getAccountId(),
              resource.getPatchKey().getFileName());
      return Response.none();
    }
  }

  private Reviewed() {}
}
