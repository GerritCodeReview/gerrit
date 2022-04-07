// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.args4j.Option;

@CommandMetaData(
    name = "check-project-visibility",
    description = "Check project visibility to a specific user",
    runsAt = MASTER_OR_SLAVE)
public class CheckProjectVisibilityCommand extends SshCommand {
  @Inject private AccountResolver accountResolver;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private PermissionBackend permissionBackend;

  @Option(
      name = "--project",
      aliases = {"-p"},
      metaVar = "PROJECT",
      required = true,
      usage = "project name to check")
  private String projectName;

  @Option(
      name = "--user",
      aliases = {"-u"},
      metaVar = "USER",
      required = true,
      usage =
          "User information to find: LastName,\\ Firstname,  email@address.com, account id or an user name. "
              + "Be sure to double-escape spaces, for example: \"check-project-visibility -p All-Projects --user Last,\\\\ First\"")
  private String userName;

  @Override
  protected void run() throws Failure, org.eclipse.jgit.errors.ConfigInvalidException, IOException {
    try {
      stdout.print(checkVisibility(userName, projectName));
    } catch (org.eclipse.jgit.errors.ConfigInvalidException
        | ResourceNotFoundException
        | IllegalArgumentException e) {
      throw die(e);
    }
  }

  String checkVisibility(String userName, String projectName)
      throws org.eclipse.jgit.errors.ConfigInvalidException, IOException,
          ResourceNotFoundException {
    return getUserList(userName).stream()
        .map(
            user -> {
              try {
                permissionBackend
                    .user(user)
                    .project(Project.nameKey(projectName))
                    .check(ProjectPermission.READ);
                return (String.format("%s %s true\n", user.getName(), user.getLoggableName()));
              } catch (AuthException | PermissionBackendException e) {
                return (String.format("%s %s false\n", user.getName(), user.getLoggableName()));
              }
            })
        .collect(Collectors.joining(""));
  }

  private Set<IdentifiedUser> getUserList(String userName)
      throws org.eclipse.jgit.errors.ConfigInvalidException, IOException,
          ResourceNotFoundException {
    return getIdList(userName).stream().map(userFactory::create).collect(Collectors.toSet());
  }

  private Set<Account.Id> getIdList(String userName)
      throws org.eclipse.jgit.errors.ConfigInvalidException, IOException,
          ResourceNotFoundException {
    Set<Account.Id> idList = accountResolver.resolve(userName).asIdSet();
    if (idList.isEmpty()) {
      throw new ResourceNotFoundException(
          "No accounts found for your query: \""
              + userName
              + "\""
              + " Tip: Try double-escaping spaces, for example: \"--user Last,\\\\ First\"");
    }
    return idList;
  }
}
