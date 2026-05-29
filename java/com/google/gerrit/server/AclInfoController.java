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

import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Singleton;
import java.util.Optional;

/** Class to control when ACL infos should be collected and be returned to the user. */
// TODO: re-enable this class if it's found safe, otherwise remove it
@Singleton
public class AclInfoController {
  /**
   * Enable ACL logging if the user has the "View Access" capability.
   *
   * @param traceContext the trace context on which ACL logging enabled if the user has the "View
   *     Access" capability.
   * @throws PermissionBackendException thrown if there is a failure while checking permissions
   */
  public void enableAclLoggingIfUserCanViewAccess(TraceContext traceContext)
      throws PermissionBackendException {
    // intentionally disabled
  }

  /**
   * Returns message containing the ACL logs that have been collected for the request, {@link
   * Optional#empty()} if ACL logging hasn't been turned on
   */
  public Optional<String> getAclInfoMessage() {
    // intentionally disabled
    return Optional.empty();
  }
}
