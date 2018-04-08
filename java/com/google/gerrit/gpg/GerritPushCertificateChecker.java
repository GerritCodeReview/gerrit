// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.gpg;

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;

public class GerritPushCertificateChecker extends PushCertificateChecker {
  public interface Factory {
    GerritPushCertificateChecker create(IdentifiedUser expectedUser);
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;

  @Inject
  GerritPushCertificateChecker(
      GerritPublicKeyChecker.Factory keyCheckerFactory,
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      @Assisted IdentifiedUser expectedUser) {
    super(keyCheckerFactory.create().setExpectedUser(expectedUser));
    this.repoManager = repoManager;
    this.allUsers = allUsers;
  }

  @Override
  protected Repository getRepository() throws IOException {
    return repoManager.openRepository(allUsers);
  }

  @Override
  protected boolean shouldClose(Repository repo) {
    return true;
  }
}
