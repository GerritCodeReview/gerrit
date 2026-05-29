// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/**
 * Checker that ensures that all Git commits that should be validated are readable using any {@link
 * ObjectReader} on the repo. It is easy for users of the JGit API to forget to call {@code flush}
 * on ObjectInserter which creates an illegal state for CommitValidators. This safeguard makes sure
 * that any functionality tested in acceptance tests got this right.
 */
@Singleton
public class GitObjectVisibilityChecker implements CommitValidationListener {
  private final GitRepositoryManager gitRepositoryManager;

  @Inject
  GitObjectVisibilityChecker(GitRepositoryManager gitRepositoryManager) {
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    try {
      try (Repository repo = gitRepositoryManager.openRepository(receiveEvent.getProjectNameKey());
          ObjectReader reader = repo.newObjectReader()) {
        if (!reader.has(receiveEvent.commit)) {
          throw new IllegalStateException(
              String.format(
                  "Commit %s was not visible using a new object reader in the repo. This creates an"
                      + " illegal state for commit validators. You must flush any ObjectReaders"
                      + " before performing the ref transaction.",
                  receiveEvent.commit));
        }
      }
    } catch (IOException e) {
      throw new StorageException(e);
    }
    return Collections.emptyList();
  }

  @Override
  public boolean shouldValidateAllCommits() {
    return true;
  }
}
