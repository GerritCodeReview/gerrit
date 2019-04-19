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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;

public class Reviewed {

  @Singleton
  public static class PutReviewed implements RestModifyView<FileResource, Input> {
    private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;

    @Inject
    PutReviewed(PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore) {
      this.accountPatchReviewStore = accountPatchReviewStore;
    }

    @Override
    public Response<String> apply(FileResource resource, Input input) {
      boolean reviewFlagUpdated =
          accountPatchReviewStore.call(
              s ->
                  s.markReviewed(
                      resource.getPatchKey().getParentKey(),
                      resource.getAccountId(),
                      resource.getPatchKey().fileName()));
      return reviewFlagUpdated ? Response.created("") : Response.ok("");
    }
  }

  @Singleton
  public static class DeleteReviewed implements RestModifyView<FileResource, Input> {
    private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;

    @Inject
    DeleteReviewed(PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore) {
      this.accountPatchReviewStore = accountPatchReviewStore;
    }

    @Override
    public Response<?> apply(FileResource resource, Input input) {
      accountPatchReviewStore.run(
          s ->
              s.clearReviewed(
                  resource.getPatchKey().getParentKey(),
                  resource.getAccountId(),
                  resource.getPatchKey().fileName()));
      return Response.none();
    }
  }

  private Reviewed() {}
}
