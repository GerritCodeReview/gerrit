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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.git.ChangeCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.Map;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "ls-user-refs", descr = "List refs visible to a specific user")
public class LsUserRefs extends SshCommand {
  @Inject
  private AccountResolver accountResolver;

  @Inject
  private IdentifiedUser.GenericFactory userFactory;

  @Inject
  private ReviewDb db;

  @Inject
  private TagCache tagCache;

  @Inject
  private ChangeCache changeCache;

  @Option(name = "--project", aliases = {"-p"}, metaVar = "PROJECT",
      usage = "project for which the refs should be listed")
  private ProjectControl projectControl;

  @Option(name = "--user", aliases = {"-u"},  metaVar = "USER",
      usage = "user for which the groups should be listed")
  private String userName;

  @Option(name = "--only-refs-heads", usage = "list only refs under refs/heads")
  private boolean onlyRefsHeads;

  @Inject
  private GitRepositoryManager repoManager;

  @Override
  protected void run() throws Failure {
    if (userName == null || projectControl == null) {
      throw new UnloggedFailure(1, "fatal: --user and --project options must be used.");
    }

    Account userAccount = null;
    try {
      userAccount = accountResolver.find(userName);
    } catch (OrmException e) {
      throw die(e);
    }

    if (userAccount == null) {
      stdout.print("No single user could be found when searching for: " + userName + '\n');
      stdout.flush();
      return;
    }

    IdentifiedUser user = userFactory.create(userAccount.getId());
    ProjectControl userProjectControl = projectControl.forUser(user);
    Repository repo = null;
    try {
      repo = repoManager.openRepository(userProjectControl.getProject()
              .getNameKey());

      Map<String, Ref> refsMap =
          new VisibleRefFilter(tagCache, changeCache, repo, userProjectControl,
              db, true).filter(repo.getAllRefs(), false);

      for (final String ref : refsMap.keySet()) {
        if (!onlyRefsHeads || ref.startsWith(Branch.R_HEADS)) {
          stdout.println(ref);
        }
      }
    } catch (RepositoryNotFoundException e) {
      throw new UnloggedFailure("fatal: '"
          + projectControl.getProject().getNameKey() + "': not a git archive");
    } catch (IOException e) {
      throw new UnloggedFailure("fatal: Error opening: '"
          + projectControl.getProject().getNameKey());
    } finally {
      repo.close();
    }
  }
}
