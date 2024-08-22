// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/** Class to control when ACL infos should be collected and be returned to the user. */
@Singleton
public class AclInfoController {
  private final PermissionBackend permissionBackend;

  @Inject
  AclInfoController(PermissionBackend permissionBackend) {
    this.permissionBackend = permissionBackend;
  }

  public void enableAclLoggingIfUserCanViewAccess(TraceContext traceContext)
      throws PermissionBackendException {
    if (canViewAclInfos()) {
      traceContext.enableAclLogging();
    }
  }

  /**
   * Returns message containing the ACL logs that have been collected for the request, {@link
   * Optional#empty()} if ACL logging hasn't been turned on
   */
  public Optional<String> getAclInfoMessage() {
    // ACL logging is only enabled if the user can view ACL infos. This is checked when ACL logging
    // is turned on in enableAclLoggingIfUserCanViewAccess. Hence we can return ACL infos if ACL
    // logging is on and do not need to check the permission again. We want to avoid re-checking the
    // permission so that we do not need to handle PermissionBackendException.
    if (!LoggingContext.getInstance().isAclLogging()) {
      return Optional.empty();
    }

    ImmutableList<String> aclLogRecords = TraceContext.getAclLogRecords();
    if (aclLogRecords.isEmpty()) {
      aclLogRecords = ImmutableList.of("Found no rules that apply, so defaulting to no permission");
    }

    StringBuilder msgBuilder = new StringBuilder("ACL info:");
    aclLogRecords.forEach(aclLogRecord -> msgBuilder.append("\n* ").append(aclLogRecord));
    return Optional.of(msgBuilder.toString());
  }

  private boolean canViewAclInfos() throws PermissionBackendException {
    return permissionBackend.currentUser().test(GlobalPermission.VIEW_ACCESS);
  }
}
