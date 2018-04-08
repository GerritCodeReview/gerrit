// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db.testing;

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Test utilities for low-level NoteDb groups. */
public class GroupTestUtil {
  public static void updateGroupFile(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      PersonIdent serverIdent,
      String refName,
      String fileName,
      String content)
      throws Exception {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      updateGroupFile(repo, serverIdent, refName, fileName, content);
    }
  }

  public static void updateGroupFile(
      Repository allUsersRepo,
      PersonIdent serverIdent,
      String refName,
      String fileName,
      String contents)
      throws Exception {
    try (RevWalk rw = new RevWalk(allUsersRepo)) {
      TestRepository<Repository> testRepository = new TestRepository<>(allUsersRepo, rw);
      TestRepository<Repository>.CommitBuilder builder =
          testRepository
              .branch(refName)
              .commit()
              .add(fileName, contents)
              .message("update group file")
              .author(serverIdent)
              .committer(serverIdent);

      Ref ref = allUsersRepo.exactRef(refName);
      if (ref != null) {
        RevCommit c = rw.parseCommit(ref.getObjectId());
        if (c != null) {
          builder.parent(c);
        }
      }
      builder.create();
    }
  }

  private GroupTestUtil() {}
}
