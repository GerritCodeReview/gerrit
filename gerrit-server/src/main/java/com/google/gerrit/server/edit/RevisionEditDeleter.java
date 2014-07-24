// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;

/* Revision edit deleter command */
public class RevisionEditDeleter {

  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;

  @Inject
  public RevisionEditDeleter(GitRepositoryManager gitManager,
      Provider<CurrentUser> currentUser) {
    this.gitManager = gitManager;
    this.currentUser = currentUser;
  }

  public void delete(Change change, PatchSet ps)
      throws AuthException, NoSuchChangeException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    Repository repo = gitManager.openRepository(change.getProject());

    IdentifiedUser me = (IdentifiedUser) currentUser.get();
    RevisionEdit edit = new RevisionEdit(me, ps.getId());
    try {
      RevWalk rw = new RevWalk(repo);
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      try {
        RevCommit commit = edit.getCommit(repo, rw);
        if (commit == null) {
          throw new NoSuchChangeException(change.getId());
        }
        ru.addCommand(new ReceiveCommand(commit, ObjectId.zeroId(), edit
            .getRefName()));
        ru.execute(rw, NullProgressMonitor.INSTANCE);
      } finally {
        rw.release();
      }
      for (ReceiveCommand cmd : ru.getCommands()) {
        if (cmd.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException("failed to delete: " + cmd);
        }
      }
    } finally {
      repo.close();
    }
  }
}
