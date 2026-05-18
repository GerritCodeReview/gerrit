// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.api.changes.ChangePermissionsInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.DefaultPermissionMappings;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Singleton;

@Singleton
public class GetChangePermissions implements RestReadView<ChangeResource> {

  /** Change-scoped permissions surfaced through this endpoint. */
  private static final ImmutableSet<ChangePermission> EXPOSED_PERMISSIONS =
      Sets.immutableEnumSet(ChangePermission.DELETE_COMMENT, ChangePermission.AI_REVIEW);

  @Override
  public Response<ChangePermissionsInfo> apply(ChangeResource rsrc)
      throws PermissionBackendException {
    ChangePermissionsInfo info = new ChangePermissionsInfo();
    for (ChangePermission permission : EXPOSED_PERMISSIONS) {
      if (rsrc.permissions().testOrFalse(permission)) {
        info.permissions.add(
            DefaultPermissionMappings.changePermissionName(permission)
                .orElseThrow(
                    () -> new IllegalStateException("no permission name for " + permission)));
      }
    }
    return Response.ok(info);
  }
}
