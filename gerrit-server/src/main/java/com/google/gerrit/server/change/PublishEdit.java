// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.changes.RevisionId;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PublishEdit.Input;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;

public class PublishEdit implements RestModifyView<RevisionResource, Input> {
  static class Input {
  }

  private final GitRepositoryManager repoManager;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> user;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeJson json;

  @Inject
  PublishEdit(GitRepositoryManager repoManager,
      Provider<ReviewDb> dbProvider,
      Provider<CurrentUser> user,
      PatchSetInfoFactory patchSetInfoFactory,
      ChangeJson json) {
    this.repoManager = repoManager;
    this.dbProvider = dbProvider;
    this.user = user;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.json = json;
    json.addOption(ListChangesOption.ALL_REVISIONS);
  }

  @Override
  public Object apply(RevisionResource rsrc, Input input) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    rsrc.checkEdit();
    PatchSet basePs = rsrc.getPatchSet();
    IdentifiedUser iu = checkIdentifiedUser();
    Repository repo =
        repoManager.openRepository(rsrc.getChange().getProject());
    RevisionEdit edit = new RevisionEdit(iu, new RevisionId(
        rsrc.getPatchSet().getId(), true));
    try {
      RevCommit commit = getCommit(repo, edit);
      if (commit == null) {
        throw new ResourceNotFoundException();
      }
      ReviewDb db = dbProvider.get();
      Change c;
      try {
        // TODO: This is missing a bunch of stuff, should use ChangeUtil instead.
        db.changes().beginTransaction(rsrc.getChange().getId());
        c = db.changes().get(basePs.getId().getParentKey());
        PatchSet ps = new PatchSet(new PatchSet.Id(c.getId(),
            c.currentPatchSetId().get() + 1));
        ps.setRevision(new RevId(ObjectId.toString(commit)));
        ps.setUploader(iu.getAccountId());
        ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        c.setCurrentPatchSet(patchSetInfoFactory.get(commit, ps.getId()));
        db.changes().update(Collections.singleton(c));
        db.patchSets().insert(Collections.singleton(ps));
        db.commit();

        BatchRefUpdate ru = repo.getRefDatabase().newBatchUpdate();
        ru.addCommand(new ReceiveCommand(commit, ObjectId.zeroId(), edit.toRefName()));
        ru.addCommand(new ReceiveCommand(ObjectId.zeroId(), commit, ps.getId().toRefName()));
        RevWalk rw = new RevWalk(repo);
        try {
          ru.execute(rw, NullProgressMonitor.INSTANCE);
        } finally {
          rw.release();
        }
        for (ReceiveCommand cmd : ru.getCommands()) {
          if (cmd.getResult() != Result.OK) {
            throw new IOException("failed to update: " + cmd);
          }
        }

        // TODO(dborowitz): Avoid rereading; ChangeData doesn't currently allow
        // manually caching the known-current PatchSet.
        return json.format(c);
      } finally {
        db.rollback();
      }
    } finally {
      repo.close();
    }
  }

  private IdentifiedUser checkIdentifiedUser() throws AuthException {
    CurrentUser u = user.get();
    if (!(u instanceof IdentifiedUser)) {
      throw new AuthException("edits only available to authenticated users");
    }
    return (IdentifiedUser) u;
  }

  private RevCommit getCommit(Repository repo, RevisionEdit edit)
      throws ResourceNotFoundException, IOException {
    try {
      RevWalk rw = new RevWalk(repo);
      try {
        return edit.get(repo, rw);
      } finally {
        rw.release();
      }
    } finally {
      repo.close();
    }
  }
}
