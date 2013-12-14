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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
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
import java.sql.Timestamp;

/* Revision edit publisher command */
public class RevisionEditPublisher {

  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeControl.GenericFactory changeControlFactory;

  @Inject
  public RevisionEditPublisher(GitRepositoryManager gitManager,
      PatchSetInserter.Factory patchSetInserterFactory,
      ChangeControl.GenericFactory changeControlFactory,
      Provider<CurrentUser> currentUser) {
    this.gitManager = gitManager;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.changeControlFactory = changeControlFactory;
    this.currentUser = currentUser;
  }

  public void publish(Change c, PatchSet basePs)
      throws AuthException, NoSuchChangeException, IOException,
      InvalidChangeOperationException, OrmException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    IdentifiedUser me = (IdentifiedUser)currentUser.get();
    Project.NameKey project = c.getProject();
    RevisionEdit edit = new RevisionEdit(me,
        PatchSet.Id.editFrom(basePs.getId()));

    Repository repo = gitManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(repo);
      BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
      try {
        RevCommit commit = edit.getCommit(repo, rw);
        if (commit == null) {
          throw new NoSuchChangeException(c.getId());
        }

        PatchSet ps =
            new PatchSet(new PatchSet.Id(c.getId(),
                c.currentPatchSetId().get() + 1));
        ps.setRevision(new RevId(ObjectId.toString(commit)));
        ps.setUploader(me.getAccountId());
        ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));

        ru.addCommand(new ReceiveCommand(commit, ObjectId.zeroId(), edit
            .getRefName()));

        PatchSetInserter inserter =
            patchSetInserterFactory.create(repo, rw,
                changeControlFactory.controlFor(c, me), commit);
        inserter.setPatchSet(ps)
            .setMessage(String.format("Patch Set %d: New edit was published",
                basePs.getPatchSetId())).insert();
        ru.execute(rw, NullProgressMonitor.INSTANCE);
      } finally {
        rw.release();
      }
      for (ReceiveCommand cmd : ru.getCommands()) {
        if (cmd.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException("failed to update: " + cmd);
        }
      }
    } finally {
      repo.close();
    }
  }
}
