// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

/** Opens a query processor. */
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ACCESS_DATABASE)
@CommandMetaData(name = "gsql", description = "Administrative interface to active database")
final class AdminQueryShell extends SshCommand {
  @Inject private PermissionBackend permissionBackend;
  @Inject private QueryShell.Factory factory;
  @Inject private IdentifiedUser currentUser;

  @Option(name = "--format", usage = "Set output format")
  private QueryShell.OutputFormat format = QueryShell.OutputFormat.PRETTY;

  @Option(name = "-c", metaVar = "SQL QUERY", usage = "Query to execute")
  private String query;

  @Override
  protected void run() throws Failure {
    try {
      permissionBackend.user(currentUser).check(GlobalPermission.ACCESS_DATABASE);
    } catch (AuthException err) {
      throw die(err.getMessage());
    } catch (PermissionBackendException e) {
      throw new Failure(1, "unavailable", e);
    }

    QueryShell shell = factory.create(in, out);
    shell.setOutputFormat(format);
    if (query != null) {
      shell.execute(query);
    } else {
      shell.run();
    }
  }
}
