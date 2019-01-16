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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Index extends RetryingRestModifyView<ChangeResource, Input, Response<?>> {
  private final PermissionBackend permissionBackend;
  private final ChangeIndexer indexer;

  @Inject
  Index(RetryHelper retryHelper, PermissionBackend permissionBackend, ChangeIndexer indexer) {
    super(retryHelper);
    this.permissionBackend = permissionBackend;
    this.indexer = indexer;
  }

  @Override
  protected Response<?> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input input)
      throws IOException, AuthException, PermissionBackendException {
    permissionBackend.currentUser().check(GlobalPermission.MAINTAIN_SERVER);
    indexer.index(rsrc.getChange());
    return Response.none();
  }
}
