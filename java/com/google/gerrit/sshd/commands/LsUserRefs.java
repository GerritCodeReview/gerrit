// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.READ_AS)
@CommandMetaData(
    name = "ls-user-refs",
    description = "List refs visible to a specific user",
    runsAt = MASTER_OR_SLAVE)
public class LsUserRefs extends SshCommand {
  @Inject private AccountResolver accountResolver;
  @Inject private OneOffRequestContext requestContext;
  @Inject private PermissionBackend permissionBackend;
  @Inject private GitRepositoryManager repoManager;

  @Option(
      name = "--project",
      aliases = {"-p"},
      metaVar = "PROJECT",
      required = true,
      usage = "project for which the refs should be listed")
  private ProjectState projectState;

  @Option(
      name = "--user",
      aliases = {"-u"},
      metaVar = "USER",
      required = true,
      usage = "user for which the groups should be listed")
  private String userName;

  @Option(name = "--only-refs-heads", usage = "list only refs under refs/heads")
  private boolean onlyRefsHeads;

  @Override
  protected void run() throws Failure {
    Account.Id userAccountId;
    try {
      userAccountId = accountResolver.resolve(userName).asUnique().getAccount().getId();
    } catch (UnprocessableEntityException e) {
      stdout.println(e.getMessage());
      stdout.flush();
      return;
    } catch (StorageException | IOException | ConfigInvalidException e) {
      throw die(e);
    }

    Project.NameKey projectName = projectState.getNameKey();
    try (Repository repo = repoManager.openRepository(projectName);
        ManualRequestContext ctx = requestContext.openAs(userAccountId)) {
      try {
        Map<String, Ref> refsMap =
            permissionBackend
                .user(user)
                .project(projectName)
                .filter(repo.getRefDatabase().getRefs(), repo, RefFilterOptions.defaults());

        for (String ref : refsMap.keySet()) {
          if (!onlyRefsHeads || ref.startsWith(RefNames.REFS_HEADS)) {
            stdout.println(ref);
          }
        }
      } catch (IOException | PermissionBackendException e) {
        throw new Failure(1, "fatal: Error reading refs: '" + projectName, e);
      }
    } catch (RepositoryNotFoundException e) {
      throw die("'" + projectName + "': not a git archive");
    } catch (IOException e) {
      throw die("Error opening: '" + projectName);
    }
  }
}
