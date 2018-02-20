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

package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.common.data.GlobalCapability.FLUSH_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.CacheResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@RequiresAnyCapability({FLUSH_CACHES, MAINTAIN_SERVER})
@Singleton
public class FlushCache implements RestModifyView<CacheResource, Input> {

  public static final String WEB_SESSIONS = "web_sessions";

  private final PermissionBackend permissionBackend;

  @Inject
  public FlushCache(PermissionBackend permissionBackend) {
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<String> apply(CacheResource rsrc, Input input)
      throws AuthException, PermissionBackendException {
    if (WEB_SESSIONS.equals(rsrc.getName())) {
      permissionBackend.currentUser().check(GlobalPermission.MAINTAIN_SERVER);
    }

    rsrc.getCache().invalidateAll();
    return Response.ok("");
  }
}
